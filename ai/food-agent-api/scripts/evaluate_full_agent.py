"""Execute and optionally publish reference-scored controlled/answer/live Agent suites."""
from __future__ import annotations

import argparse
import importlib
import json
import logging
import sys
from pathlib import Path
from time import perf_counter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import get_settings
from app.core.langsmith import (
    client_for,
    close_langsmith,
    finish_trace,
    observation_scope,
    redact,
)
from evaluation.agent_evaluation import dataset_digest, sync_dataset
from evaluation.full_evaluation import (
    SCORING_REVISION,
    passes_gate,
    source_manifest,
    suite_evaluator,
    suite_scores,
    summarize_suite,
    validate_reference,
)


def get_suite(name):
    return importlib.import_module(f"evaluation.{name}_evaluation")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite", choices=("controlled", "answer", "live"), required=True)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--repetitions", type=int, default=1)
    parser.add_argument("--min-score", type=float, default=1.0)
    parser.add_argument("--experiment-prefix", default="food-agent-complete")
    parser.add_argument("--dataset-prefix", default="food-agent")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    if not 1 <= args.repetitions <= 10 or not 0 <= args.min_score <= 1:
        parser.error("repetitions must be 1..10; min-score must be 0..1")
    settings = get_settings()
    if args.publish and not settings.langsmith_api_key.strip():
        parser.error("--publish needs LANGSMITH_API_KEY")
    suite = get_suite(args.suite)
    cases = suite.load_cases()
    if not cases or len({case["id"] for case in cases}) != len(cases):
        parser.error("Dataset must have unique cases")
    for case in cases:
        validate_reference(case["outputs"])
    expanded = [{**case, "id": f"{case['id']}-trial-{trial}",
                 "inputs": {**case["inputs"], "trial": trial}}
                for trial in range(1, args.repetitions + 1) for case in cases]
    by_id = {case["id"]: case for case in cases}
    rows = []
    metadata = {"suite": args.suite, "revision": settings.langsmith_revision, "scoring_revision": SCORING_REVISION,
                "model": settings.llm_model, "embedding_model": settings.embedding_model,
                "repetitions": args.repetitions, "dataset_sha256": dataset_digest(expanded),
                "source_sha256": source_manifest(ROOT)["sha256"], "execution_mode": "execute",
                "environment": settings.langsmith_environment, "min_score": args.min_score,
                "cost_status": "not_estimated_without_provider_usage_and_price"}
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save(final=False, **details):
        report = summarize_suite(rows, expected_case_ids=list(by_id), repetitions=args.repetitions)
        report.update(metadata=metadata, suite=args.suite, final=final, **details)
        report["passed"] = passes_gate(report, min_score=args.min_score)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return report

    def target(inputs):
        started = perf_counter()
        with observation_scope(settings, "evaluation." + args.suite, inputs=inputs, enabled=args.publish) as run:
            try:
                outputs = suite.run_case({key: value for key, value in inputs.items() if key != "trial"}, settings)
            except Exception as exc:  # noqa: BLE001 - preserve per-case failures and continue the frozen dataset.
                outputs = {"checks": {}, "error": type(exc).__name__, "execution_tier": args.suite + "_error"}
                logging.getLogger(__name__).warning("case_failed case=%s error=%s", inputs["case_id"], type(exc).__name__)
            outputs["duration_ms"] = round((perf_counter() - started) * 1000, 2)
            outputs = redact(outputs, secrets=(settings.langsmith_api_key, settings.llm_api_key))
            finish_trace(run, outputs)
        case = by_id[inputs["case_id"]]
        rows.append({"id": inputs["case_id"], "trial": inputs["trial"], "category": case.get("category", "uncategorized"),
                     "reference": case["outputs"], "outputs": outputs})
        save()
        success = bool(suite_scores(outputs, case["outputs"])["task_success"])
        print(json.dumps({"case": inputs["case_id"], "trial": inputs["trial"], "passed": success,
                          "error": outputs.get("error"), "duration_ms": outputs["duration_ms"]}), flush=True)
        return outputs

    details = {}
    try:
        if args.publish:
            client = client_for(settings)
            name = sync_dataset(client, args.dataset_prefix, args.suite, expanded)
            experiment = client.evaluate(target, data=name, evaluators=[suite_evaluator],
                experiment_prefix=args.experiment_prefix + "-" + args.suite, metadata=metadata,
                max_concurrency=1, blocking=True)
            evaluated = list(experiment)
            details.update(dataset_name=name, experiment_name=experiment.experiment_name,
                           evaluated_rows=len(evaluated))
            project = client.read_project(project_name=experiment.experiment_name)
            details["experiment_id"] = str(project.id)
            details["experiment_url"] = project.url
        else:
            for case in expanded:
                target(case["inputs"])
        report = save(final=True, **details)
        print(json.dumps({key: report[key] for key in ("suite", "task_success", "safety_failed_assertions",
                         "safety_unverified_assertions", "errors", "passed")}, ensure_ascii=True), flush=True)
        return 0 if report["passed"] else 1
    finally:
        close_langsmith()


if __name__ == "__main__":
    raise SystemExit(main())
