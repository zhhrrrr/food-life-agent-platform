"""Reference-defined assertions for full Agent experiments; no implicit passes."""
from __future__ import annotations

import hashlib
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

SCORING_REVISION = "full-evaluation-4"


def validate_reference(reference: dict) -> tuple[list[str], list[str]]:
    required, safety = reference.get("required_checks"), reference.get("safety_checks", [])
    for names in (required, safety):
        if not isinstance(names, list) or any(not isinstance(name, str) or not name for name in names):
            raise ValueError("Check names must be nonempty strings")
        if len(set(names)) != len(names):
            raise ValueError("Check names must be unique")
    if not required or not set(safety).issubset(required):
        raise ValueError("Need required checks; safety checks must be a subset")
    return required, safety


def suite_scores(outputs: dict, reference_outputs: dict) -> dict[str, float]:
    required, safety = validate_reference(reference_outputs)
    checks = outputs.get("checks")
    if not isinstance(checks, dict):
        checks = {}
    unverified = outputs.get("unverified_checks", [])
    unverified_valid = (isinstance(unverified, list) and all(isinstance(name, str) for name in unverified)
                        and len(set(unverified)) == len(unverified) and set(unverified).issubset(required))
    valid = (set(checks) == set(required) and all(type(value) is bool for value in checks.values())
             and unverified_valid)
    unknown = set(unverified) if unverified_valid else set(required)
    scores = {"assertion_schema_valid": float(valid),
              "task_success": float(valid and not unknown and not outputs.get("error") and all(checks[name] for name in required))}
    scores.update({"check." + name: float(checks.get(name) is True and name not in unknown) for name in required})
    if safety:
        scores["safety_success"] = float(all(checks.get(name) is True and name not in unknown for name in safety))
    return scores


def suite_evaluator(inputs: dict, outputs: dict, reference_outputs: dict) -> dict:
    return {"results": [{"key": key, "score": value}
                        for key, value in suite_scores(outputs or {}, reference_outputs).items()]}


def load_suite_cases(path: Path) -> list[dict]:
    cases = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    ids = []
    for case in cases:
        identifier = case.get("id")
        if not isinstance(identifier, str) or not identifier or case.get("inputs", {}).get("case_id") != identifier:
            raise ValueError("Cases need a stable ID matching inputs.case_id")
        validate_reference(case["outputs"])
        ids.append(identifier)
    if not ids or len(ids) != len(set(ids)):
        raise ValueError("Dataset must be nonempty with unique case IDs")
    return cases


def _fraction(values) -> dict:
    return {"passed": sum(values), "total": len(values), "rate": sum(values) / len(values) if values else None}


def _p95(values):
    return sorted(values)[max(0, math.ceil(len(values) * 0.95) - 1)] if values else None


def summarize_suite(rows: list[dict], *, expected_case_ids: list[str], repetitions: int) -> dict:
    if not expected_case_ids or len(set(expected_case_ids)) != len(expected_case_ids) or repetitions < 1:
        raise ValueError("Need unique expected cases and positive repetition count")
    seen, metrics, categories, trials, per_case = set(), defaultdict(list), defaultdict(list), defaultdict(list), defaultdict(list)
    safety_failed = safety_unverified = 0
    for row in rows:
        identity = (row["id"], row["trial"])
        if identity in seen or row["id"] not in expected_case_ids or not 1 <= row["trial"] <= repetitions:
            raise ValueError("Unexpected or duplicate case/trial")
        seen.add(identity)
        scores = suite_scores(row["outputs"], row["reference"])
        row["scores"] = scores
        for key, value in scores.items():
            metrics[key].append(value)
        success = scores["task_success"]
        categories[row.get("category", "uncategorized")].append(success)
        trials[row["trial"]].append(success)
        per_case[row["id"]].append(success)
        checks = row["outputs"].get("checks") or {}
        if not isinstance(checks, dict):
            checks = {}
        raw_unknown = row["outputs"].get("unverified_checks", [])
        required = row["reference"]["required_checks"]
        unknown_valid = (isinstance(raw_unknown, list) and all(isinstance(name, str) for name in raw_unknown)
                         and len(set(raw_unknown)) == len(raw_unknown) and set(raw_unknown).issubset(required))
        unknown = raw_unknown if unknown_valid else required
        for name in row["reference"].get("safety_checks", []):
            if name in unknown:
                safety_unverified += 1
            elif checks.get(name) is False:
                safety_failed += 1
            elif checks.get(name) is not True:
                safety_unverified += 1
    incomplete = sorted(identifier for identifier in expected_case_ids if len(per_case[identifier]) != repetitions)
    stable = sum(len(per_case[identifier]) == repetitions and all(per_case[identifier]) for identifier in expected_case_ids)
    durations = [row["outputs"]["duration_ms"] for row in rows
                 if isinstance(row["outputs"].get("duration_ms"), (int, float))]
    tool_counts = [row["outputs"]["tool_call_count"] for row in rows
                   if isinstance(row["outputs"].get("tool_call_count"), int)]
    return {"scoring_revision": SCORING_REVISION, "observations": len(rows), "expected_observations": len(expected_case_ids) * repetitions,
            "task_success": _fraction(metrics.get("task_success", [])),
            "metrics": {key: _fraction(values) for key, values in sorted(metrics.items())},
            "categories": {key: _fraction(values) for key, values in sorted(categories.items())},
            "trials": {str(key): _fraction(values) for key, values in sorted(trials.items())},
            "reliability": {"all_trials_passed": stable, "cases": len(expected_case_ids),
                            "rate": stable / len(expected_case_ids), "incomplete_cases": incomplete,
                            "changed_outcome_cases": sorted(key for key, values in per_case.items() if len(set(values)) > 1)},
            "safety_failed_assertions": safety_failed, "safety_unverified_assertions": safety_unverified,
            "errors": sum(bool(row["outputs"].get("error")) for row in rows),
            "execution_tiers": dict(Counter(row["outputs"].get("execution_tier", "unknown") for row in rows)),
            "latency_p95_ms": _p95(durations),
            "tool_calls": {"observed_cases": len(tool_counts), "total": sum(tool_counts) if tool_counts else None},
            "results": rows}


def passes_gate(report: dict, *, min_score: float) -> bool:
    return bool(report["observations"] == report["expected_observations"]
                and not report["reliability"]["incomplete_cases"]
                and report["errors"] == 0 and report["safety_failed_assertions"] == 0
                and report["safety_unverified_assertions"] == 0
                and report["metrics"].get("assertion_schema_valid", {}).get("rate") == 1.0
                and report["task_success"]["rate"] is not None
                and all(trial["rate"] >= min_score for trial in report["trials"].values()))


def source_manifest(root: Path) -> dict:
    """Hash code and fixtures only; never read environment/config secrets."""
    manifest = {}
    for directory, suffixes in (("app", {".py"}), ("evaluation", {".py"}), ("evals", {".jsonl"}), ("scripts", {".py"})):
        for path in sorted((root / directory).rglob("*")):
            if path.is_file() and path.suffix in suffixes:
                manifest[path.relative_to(root).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    digest = hashlib.sha256(json.dumps(manifest, sort_keys=True).encode()).hexdigest()
    return {"sha256": digest, "files": manifest}
