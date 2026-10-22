"""Deterministic evaluators shared by local regression and LangSmith experiments."""
from __future__ import annotations

import hashlib
import json
from collections import Counter
from pathlib import Path
from time import perf_counter
from uuid import NAMESPACE_URL, uuid5

from app.core.config import Settings
from app.core.langsmith import observation_scope
from app.memory.entities import explicit_ids
from app.memory.session_memory import SessionTurn
from app.orchestration.planner import DynamicPlanner
from app.orchestration.registry import CAPABILITY_REGISTRY
from app.rag.retriever import RagRetriever
from scripts.evaluate_rag import _ndcg_at_k, _percentile

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CASES = {"planner": ROOT / "evals/planner_cases.jsonl", "rag": ROOT / "evals/rag_retrieval_cases.jsonl"}


def _valid_dag(tasks: list[dict]) -> bool:
    if not tasks or len(tasks) > 12:
        return False
    if any(not isinstance(task, dict) or not isinstance(task.get("id"), str)
           or not task["id"] or not isinstance(task.get("capability"), str)
           or (task.get("trade_intent") is not None and not isinstance(task["trade_intent"], dict)) for task in tasks):
        return False
    by_id = {task["id"]: task for task in tasks}
    if len(by_id) != len(tasks):
        return False
    for task in tasks:
        dependencies = task.get("depends_on", [])
        if (not isinstance(dependencies, list) or any(not isinstance(dep, str) or dep not in by_id for dep in dependencies)
                or len(set(dependencies)) != len(dependencies)):
            return False
    visiting, visited = set(), set()
    def visit(identifier):
        if identifier in visiting:
            return False
        if identifier in visited:
            return True
        visiting.add(identifier)
        if not all(visit(dep) for dep in by_id[identifier].get("depends_on", [])):
            return False
        visiting.remove(identifier)
        visited.add(identifier)
        return True
    return all(visit(identifier) for identifier in by_id)


def _same_plan(actual, expected, *, parameters: bool, dependencies: bool) -> bool:
    """Bounded graph matching, independent of generated task IDs and list order."""
    if not _valid_dag(actual) or not _valid_dag(expected) or len(actual) != len(expected):
        return False
    candidates = {}
    def same_parameters(item, task):
        if "trade_intent" in task:
            if task["trade_intent"] is None:
                if item.get("trade_intent") is not None:
                    return False
            elif not all((item.get("trade_intent") or {}).get(key) == value
                         for key, value in task["trade_intent"].items()):
                return False
        objective = item.get("objective")
        identifiers = explicit_ids(objective) if isinstance(objective, str) else {}
        return all(identifiers.get(key) == value for key, value in task.get("objective_ids", {}).items())
    for task in expected:
        candidates[task["id"]] = [item for item in actual if item["capability"] == task["capability"]
            and (not parameters or same_parameters(item, task))]
    if any(not items for items in candidates.values()):
        return False
    order = sorted(expected, key=lambda task: len(candidates[task["id"]]))
    mapping, used = {}, set()
    remaining_states = 50000
    def match(index):
        nonlocal remaining_states
        remaining_states -= 1
        if remaining_states < 0:
            return False
        if index == len(order):
            return True
        task = order[index]
        for item in candidates[task["id"]]:
            if item["id"] in used:
                continue
            mapping[task["id"]] = item
            valid = True
            if dependencies:
                for bound in order[:index + 1]:
                    assigned = mapping[bound["id"]]
                    expected_deps, actual_deps = bound.get("depends_on", []), assigned.get("depends_on", [])
                    if len(expected_deps) != len(actual_deps) or any(
                        (other["id"] in expected_deps) != (mapping[other["id"]]["id"] in actual_deps)
                        for other in order[:index + 1]
                    ):
                        valid = False
                        break
            if valid:
                used.add(item["id"])
                if match(index + 1):
                    return True
                used.remove(item["id"])
            mapping.pop(task["id"], None)
        return False
    return match(0)


def planner_scores(outputs: dict, reference_outputs: dict) -> dict[str, float]:
    actual, expected = outputs.get("tasks", []), reference_outputs["tasks"]
    valid = not outputs.get("error") and _valid_dag(actual)
    capabilities = valid and Counter(task["capability"] for task in actual) == Counter(task["capability"] for task in expected)
    source = valid and outputs.get("source") in reference_outputs.get("allowed_sources", ["llm"])
    return {
        "capabilities_correct": float(capabilities),
        "dependencies_correct": float(valid and _same_plan(actual, expected, parameters=False, dependencies=True)),
        "parameters_correct": float(valid and _same_plan(actual, expected, parameters=True, dependencies=False)),
        "source_correct": float(source),
        "strict_success": float(source and _same_plan(actual, expected, parameters=True, dependencies=True)),
    }


def rag_scores(outputs: dict, reference_outputs: dict) -> dict[str, float]:
    failed = bool(outputs.get("error")) or "actual_ids" not in outputs
    actual = outputs.get("actual_ids", [])[:outputs.get("top_k", 5)]
    if reference_outputs.get("should_reject"):
        return {"rejection_success": float(not failed and not actual)}
    if failed:
        return {"hit_rate_at_k": 0.0, "recall_at_k": 0.0, "mrr_at_k": 0.0, "ndcg_at_k": 0.0}
    expected = set(reference_outputs["expected_ids"])
    matched = expected.intersection(actual)
    match_any = reference_outputs.get("relevance_mode") == "any"
    first = next((rank for rank, identifier in enumerate(actual, 1) if identifier in expected), None)
    return {"hit_rate_at_k": float(bool(matched)),
            "recall_at_k": float(bool(matched)) if match_any else len(matched) / len(expected),
            "mrr_at_k": 1 / first if first else 0.0,
            "ndcg_at_k": _ndcg_at_k(actual, expected, outputs.get("top_k", 5), match_any=match_any)}


def planner_evaluator(inputs: dict, outputs: dict, reference_outputs: dict) -> dict:
    return {"results": [{"key": key, "score": value} for key, value in planner_scores(outputs, reference_outputs).items()]}


def rag_evaluator(inputs: dict, outputs: dict, reference_outputs: dict) -> dict:
    return {"results": [{"key": key, "score": value} for key, value in rag_scores(outputs, reference_outputs).items()]}


def load_examples(kind: str, path: Path) -> list[dict]:
    cases = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        case = json.loads(line)
        if kind == "rag":
            case = {"id": case["id"], "inputs": {key: case[key] for key in ("agent", "query", "capabilities") if key in case},
                    "outputs": {key: case[key] for key in ("expected_ids", "should_reject", "relevance_mode") if key in case}}
            expected = case["outputs"]["expected_ids"]
            if not isinstance(expected, list) or (not case["outputs"].get("should_reject") and not expected):
                raise ValueError("RAG positives need expected_ids")
            if case["inputs"]["agent"] not in {"recommend", "trade", "support"}:
                raise ValueError("Unknown RAG agent")
        else:
            if not case.get("inputs", {}).get("message", "").strip():
                raise ValueError("Planner case needs a message")
            tasks = case["outputs"]["tasks"]
            if not _valid_dag(tasks):
                raise ValueError("Invalid expected DAG or dependency")
            if any(task["capability"] not in CAPABILITY_REGISTRY for task in tasks):
                raise ValueError("Unknown expected capability")
            sources = case["outputs"].get("allowed_sources", ["llm"])
            if not sources or any(source not in {"rules", "llm"} for source in sources):
                raise ValueError("Invalid allowed_sources")
            for turn in case["inputs"].get("history", []):
                SessionTurn.model_validate(turn)
        if not isinstance(case.get("id"), str) or not case["id"]:
            raise ValueError("Each case requires a nonempty ID")
        cases.append(case)
    if not cases or len({case["id"] for case in cases}) != len(cases):
        raise ValueError("Dataset must be nonempty with unique IDs")
    return cases


def dataset_digest(cases: list[dict]) -> str:
    return hashlib.sha256(json.dumps(sorted(cases, key=lambda case: case["id"]),
        ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def sync_dataset(client, prefix: str, kind: str, cases: list[dict]) -> str:
    digest = dataset_digest(cases)
    name = f"{prefix}-{kind}-{digest[:12]}"
    dataset = (client.read_dataset(dataset_name=name) if client.has_dataset(dataset_name=name)
               else client.create_dataset(dataset_name=name, description=f"Food Agent {kind}; sha256={digest}"))
    existing = {str(example.id): example for example in client.list_examples(dataset_id=dataset.id)}
    expected_ids = {str(uuid5(NAMESPACE_URL, name + ":" + case["id"])) for case in cases}
    if set(existing) - expected_ids:
        raise ValueError("Remote dataset contains unexpected examples")
    missing = []
    for case in cases:
        identifier = str(uuid5(NAMESPACE_URL, name + ":" + case["id"]))
        if identifier in existing:
            example = existing[identifier]
            if example.inputs != case["inputs"] or example.outputs != case["outputs"]:
                raise ValueError("Remote dataset differs from its content version; use another dataset prefix")
        else:
            missing.append({"id": identifier, "inputs": case["inputs"], "outputs": case["outputs"],
                            "metadata": {"case_id": case["id"], "dataset_sha256": digest}})
    if missing:
        client.create_examples(dataset_id=dataset.id, examples=missing)
    return name


def make_target(kind: str, settings: Settings, *, publish: bool, top_k: int = 5):
    planner = DynamicPlanner(settings) if kind == "planner" else None
    retriever = RagRetriever(settings, strict=True) if kind == "rag" else None
    def target(inputs: dict) -> dict:
        started = perf_counter()
        try:
            with observation_scope(settings, f"evaluation.{kind}", inputs=inputs, enabled=publish):
                if planner is not None:
                    plan = planner.plan(inputs["message"], history=[SessionTurn.model_validate(turn) for turn in inputs.get("history", [])])
                    result = {"source": plan.source, "tasks": [task.model_dump(mode="json") for task in plan.tasks]}
                else:
                    retrieval = retriever.retrieve(inputs["agent"], inputs["query"], size=top_k,
                        original_query=inputs["query"], capabilities=inputs.get("capabilities"))
                    result = {"actual_ids": [hit.id for hit in retrieval.hits], "top_k": top_k, "retrieval_mode": retrieval.mode}
        except Exception as exc:
            result = {"error": type(exc).__name__, "tasks": [], "actual_ids": [], "top_k": top_k}
        return {**result, "duration_ms": round((perf_counter() - started) * 1000, 2)}
    return target


def summarize(kind: str, rows: list[dict]) -> dict:
    scores = {}
    for row in rows:
        for key, value in row["scores"].items():
            scores.setdefault(key, []).append(value)
    durations = [row["outputs"]["duration_ms"] for row in rows]
    report = {"kind": kind, "cases": len(rows), "errors": sum(bool(row["outputs"].get("error")) for row in rows),
              "scores": {key: sum(values) / len(values) for key, values in scores.items()},
              "latency_p95_ms": _percentile(durations, 0.95), "results": rows}
    if kind == "planner":
        report["plan_sources"] = dict(Counter(row["outputs"].get("source", "error") for row in rows))
    return report
