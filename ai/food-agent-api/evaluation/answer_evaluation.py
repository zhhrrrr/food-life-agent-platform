"""Frozen, deterministic answer checks over actual Support/Recommend chains.

This measures explicit factual requirements, not general semantic quality. Gold
rule prose and requirements live in the fixture, never in generated answers.
Strict retrieval makes service failure distinguishable from genuine no evidence.
"""
from __future__ import annotations

import hashlib
import json
import re
from pathlib import Path
from time import perf_counter
from typing import Any

from langchain_core.callbacks import BaseCallbackHandler

from app.core.config import Settings
from app.core.langsmith import redact

CASES_PATH = Path(__file__).resolve().parents[1] / "evals" / "answer_grounding_cases.jsonl"


def load_cases() -> list[dict]:
    """Read a fresh copy; never revise golds in response to model output."""
    cases = [json.loads(line) for line in CASES_PATH.read_text(encoding="utf-8").splitlines() if line.strip()]
    identifiers = [case["id"] for case in cases]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("Duplicate answer case")
    for case in cases:
        if case["inputs"]["case_id"] != case["id"]:
            raise ValueError("Mismatched answer case")
        for pattern in (*case["rubric"]["required_patterns"], *case["rubric"].get("forbidden_patterns", [])):
            re.compile(pattern)
    return cases


def _normalized(value: object) -> str:
    return re.sub(r"\s+", "", str(value))


def _is_subset(expected: Any, actual: Any) -> bool:
    if isinstance(expected, dict):
        return isinstance(actual, dict) and all(key in actual and _is_subset(value, actual[key]) for key, value in expected.items())
    if isinstance(expected, list):
        return isinstance(actual, list) and actual == expected
    return type(actual) is type(expected) and actual == expected


def _cited_context(result: dict) -> list[dict]:
    context = result.get("context", [])
    references = result.get("references", [])
    if not isinstance(context, list) or not isinstance(references, list) or not references:
        return []
    cited = []
    for reference in references:
        if not isinstance(reference, dict) or not reference.get("id") or not reference.get("source"):
            return []
        match = next((item for item in context if isinstance(item, dict)
                      and item.get("id") == reference["id"] and item.get("source") == reference["source"]
                      and isinstance(item.get("content"), str) and item["content"].strip()), None)
        if match is None:
            return []
        cited.append(match)
    return cited


def _recommendation_facts(answer: str, cited: list[dict]) -> bool:
    """Check a named option and explicit labelled numeric claims against its facts.

    Unlabelled prose, comparisons, and complete claim entailment still require
    human review. Numeric statements fail closed when ambiguous across candidates.
    """
    named = []
    for item in cited:
        metadata = item.get("metadata", {})
        names = [item.get("title"), metadata.get("shopName"), metadata.get("packageName")]
        if any(isinstance(name, str) and len(name) >= 2 and name in answer for name in names):
            named.append(item)
    if not named:
        return False
    for label, number in re.findall(r"(人均价格|人均消费|售价|现价|价格|库存|销量)[为是约：:\s]*([0-9]+(?:\.[0-9]+)?)", answer):
        field = {"人均价格": "avgPrice", "人均消费": "avgPrice", "售价": "price", "现价": "price",
                 "价格": "price", "库存": "stock", "销量": "sold"}[label]
        values = []
        for item in named:
            metadata = item.get("metadata", {})
            if (field in {"stock", "sold", "price"} and item.get("id", "").startswith("package-")
                    and (metadata.get("factSource") != "business_api" or metadata.get("factStatus") != "success")):
                continue
            raw = metadata.get(field)
            if raw is not None:
                match = re.match(r"^[0-9]+(?:\.[0-9]+)?", str(raw))
                if match:
                    values.append(float(match[0]))
        if float(number) not in values:
            return False
    return True


def evaluate_observation(case: dict, observation: dict) -> dict[str, bool]:
    """Score actual observations against independent frozen requirements."""
    result = observation.get("result", {})
    rubric = case["rubric"]
    answer = result.get("response", "")
    nonempty = isinstance(answer, str) and bool(answer.strip())
    answer = answer if isinstance(answer, str) else ""
    cited = _cited_context(result)
    grounding = bool(cited)
    if rubric.get("authority_content"):
        grounding = any(
            _normalized(item["content"]) == _normalized(rubric["authority_content"])
            and item.get("title") == rubric["authority_title"]
            and item.get("metadata", {}).get("doc_type") == "platform_rule"
            for item in cited
        )
    if rubric.get("recommendation"):
        grounding = grounding and _recommendation_facts(answer, cited)
    schema = (nonempty and isinstance(result.get("rag_mode"), str)
              and isinstance(result.get("query"), str) and isinstance(result.get("rewritten_query"), str)
              and isinstance(result.get("capabilities"), list) and bool(result["capabilities"])
              and all(isinstance(item, str) for item in result["capabilities"])
              and isinstance(result.get("context"), list) and isinstance(result.get("references"), list)
              and isinstance(result.get("session_constraints"), dict))
    model = observation.get("model", {})
    checks = {
        "nonempty_answer": nonempty,
        "response_schema": bool(schema),
        "retrieval_succeeded": observation.get("retrieval", {}).get("completed") is True,
        "real_answer_model": (result.get("rag_mode", "").endswith("_llm")
                              and model.get("answer_completed", 0) > 0),
        "answer_requirements": nonempty and all(re.search(pattern, answer, re.IGNORECASE) is not None for pattern in rubric["required_patterns"]),
        "evidence_grounding": bool(nonempty and grounding),
        "no_forbidden_claims": nonempty and not any(re.search(pattern, answer, re.IGNORECASE) for pattern in rubric.get("forbidden_patterns", [])),
    }
    if "current_constraints" in case["outputs"]["required_checks"]:
        checks["current_constraints"] = _is_subset(rubric["expected_constraints"], result.get("session_constraints", {}))
    return {key: bool(checks[key]) for key in case["outputs"]["required_checks"]}


class _ModelEvidence(BaseCallbackHandler):
    """Collect counts and provider-reported tokens without prompts or secrets."""
    def __init__(self, evidence: dict, *, answer: bool):
        self.evidence = evidence
        self.answer = answer

    def on_llm_start(self, serialized, prompts, **kwargs):
        self.evidence["calls"] += 1

    def on_chat_model_start(self, serialized, messages, **kwargs):
        self.evidence["calls"] += 1

    def on_llm_end(self, response, **kwargs):
        self.evidence["completed"] += 1
        if self.answer:
            self.evidence["answer_completed"] += 1
        usages = []
        for generations in response.generations:
            for generation in generations:
                usage = getattr(getattr(generation, "message", None), "usage_metadata", None)
                if usage:
                    usages.append(usage)
        if not usages:
            raw = (response.llm_output or {}).get("token_usage", {})
            if raw:
                usages = [{"input_tokens": raw.get("prompt_tokens"), "output_tokens": raw.get("completion_tokens"), "total_tokens": raw.get("total_tokens")}]
        for usage in usages:
            for key in ("input_tokens", "output_tokens", "total_tokens"):
                value = usage.get(key)
                if type(value) is int and value >= 0:
                    self.evidence["usage"][key] = self.evidence["usage"].get(key, 0) + value

    def on_llm_error(self, error, **kwargs):
        self.evidence["errors"].append(type(error).__name__)


class _ObservedModel:
    def __init__(self, model, evidence: dict):
        self.model, self.evidence = model, evidence

    def with_structured_output(self, schema, **kwargs):
        callback = _ModelEvidence(self.evidence, answer=schema.__name__ == "AgentAnswer")
        return self.model.with_structured_output(schema, **kwargs).with_config(callbacks=[callback])


class _ObservedRetriever:
    def __init__(self, retriever, evidence: dict):
        self.retriever, self.evidence = retriever, evidence

    def retrieve(self, *args, **kwargs):
        self.evidence["calls"] += 1
        result = self.retriever.retrieve(*args, **kwargs)
        self.evidence.update(completed=True, mode=result.mode, hit_count=len(result.hits), candidate_counts=result.candidate_counts)
        return result


class _ObservedToolExecutor:
    def __init__(self, executor, evidence: list):
        self.executor, self.evidence = executor, evidence

    def execute(self, calls, context):
        executions = self.executor.execute(calls, context)
        self.evidence.extend({"name": item.call.name, "status": item.status,
                              "attempts": item.attempts, "duration_ms": round(item.elapsed_ms, 2),
                              "used_fallback": item.used_fallback, "error_code": item.error_code}
                             for item in executions)
        return executions


def _execute_chain(inputs: dict, settings: Settings) -> dict:
    from app.llm.chat_model import build_chat_model
    from app.memory.session_memory import SessionTurn
    from app.rag.retriever import RagRetriever
    from app.recommend.chain import RecommendChain
    from app.support.chain import SupportChain

    observation = {
        "model": {"calls": 0, "completed": 0, "answer_completed": 0, "errors": [], "usage": {}},
        "retrieval": {"completed": False, "calls": 0},
        "tools": [],
    }
    try:
        retriever = _ObservedRetriever(RagRetriever(settings=settings, strict=True), observation["retrieval"])
        model = _ObservedModel(build_chat_model(settings), observation["model"])
        chain_type = {"support": SupportChain, "recommend": RecommendChain}[inputs["agent"]]
        chain = chain_type(settings=settings, retriever=retriever, chat_model=model)
        if inputs["agent"] == "recommend":
            chain.package_enricher.executor = _ObservedToolExecutor(chain.package_enricher.executor, observation["tools"])
        result = chain.run(inputs["message"], session_history=[SessionTurn.model_validate(turn) for turn in inputs.get("history", [])],
                           memory_context=inputs.get("memory_context", {}))
        observation["result"] = result.model_dump(mode="json")
    except Exception as exc:  # noqa: BLE001 - keep partial provider usage on an external failure.
        observation["error"] = type(exc).__name__
    return observation


def _safe_evidence(case: dict, observation: dict, settings: Settings) -> dict:
    result = observation.get("result", {})
    answer = result.get("response", "")
    # Retrieval identifiers are useful locally for joins, unnecessary to export.
    for reference in result.get("references", []):
        identifier = reference.get("id")
        if isinstance(identifier, str) and identifier:
            answer = answer.replace(identifier, "[reference]")
    rubric = case["rubric"]
    contexts = result.get("context", [])
    evidence = {
        "answer": answer,
        "references": [{key: ref[key] for key in ("title", "source") if key in ref} for ref in result.get("references", [])],
        "rag_mode": result.get("rag_mode"),
        "answer_used_fallback": str(result.get("rag_mode", "")).endswith("_template"),
        "answer_generation_skipped": not str(result.get("rag_mode", "")).endswith(("_llm", "_template")),
        "model": observation.get("model", {}),
        "retrieval": observation.get("retrieval", {}),
        "tools": observation.get("tools", []),
        "current_constraints": result.get("session_constraints", {}),
        "authority_source": "app/indexing/rule_document_source.py" if rubric.get("authority_content") else None,
        "authority_title": rubric.get("authority_title"),
        "authority_content_sha256": hashlib.sha256(rubric["authority_content"].encode()).hexdigest() if rubric.get("authority_content") else None,
        "context_count": len(contexts),
        "business_facts_verified": sum(item.get("metadata", {}).get("factSource") == "business_api"
                                       and item.get("metadata", {}).get("factStatus") == "success" for item in contexts),
        "cost_available": False,
        "scoring_method": "frozen_required_and_forbidden_patterns_plus_authority_content_or_candidate_facts",
        "quality_limit": "Pattern checks are not complete semantic entailment or a subjective quality rating.",
    }
    secrets = tuple(str(value) for key, value in settings.model_dump().items()
                    if re.search(r"key|secret|token|password", key, re.IGNORECASE) and isinstance(value, str) and value)
    return redact(evidence, secrets=secrets)


def run_case(inputs: dict, settings: Settings) -> dict:
    started = perf_counter()
    case = next((case for case in load_cases() if case["id"] == inputs.get("case_id")), None)
    if case is None:
        raise ValueError("Unknown answer case")
    result = {"checks": {key: False for key in case["outputs"]["required_checks"]},
              "unverified_checks": list(case["outputs"]["required_checks"]), "evidence": {"cost_available": False}}
    try:
        if inputs != case["inputs"]:
            raise ValueError("Answer evaluation inputs differ from frozen fixture")
        observation = _execute_chain(case["inputs"], settings)
        if not observation.get("error"):
            result["checks"] = evaluate_observation(case, observation)
            result["unverified_checks"] = []
        result["evidence"] = _safe_evidence(case, observation, settings)
        if observation.get("error"):
            result["error"] = observation["error"]
    except Exception as exc:  # noqa: BLE001 - public evaluation contract exports exception types only.
        result["error"] = type(exc).__name__
    return {**result, "duration_ms": round((perf_counter() - started) * 1000, 2), "execution_tier": "real_model_answer"}
