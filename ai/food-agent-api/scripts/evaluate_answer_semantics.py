"""Judge recorded answers separately from immutable strict evaluation scores."""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path

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
from evaluation.semantic_evaluation import (
    prepare_cases,
    run_judgment,
    semantic_evaluator,
    summarize_semantics,
)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--dataset-prefix", default="food-agent")
    parser.add_argument("--experiment-prefix", default="food-agent-advisory-semantics")
    args = parser.parse_args(argv)
    if args.source.resolve() == args.output.resolve():
        parser.error("Source report and semantic output must be different paths")
    settings = get_settings()
    if args.publish and not settings.langsmith_api_key.strip():
        parser.error("--publish needs LANGSMITH_API_KEY")
    raw_source = args.source.read_bytes()
    source = json.loads(raw_source.decode("utf-8"))
    try:
        cases = prepare_cases(source, settings)
    except ValueError as exc:
        parser.error(str(exc))
    metadata = {
        "suite": "answer_semantics_advisory", "advisory_only": True,
        "semantic_revision": "semantic-advisory-1", "judge_model": settings.llm_model,
        "producer_model": source.get("metadata", {}).get("model"),
        "same_model_bias": settings.llm_model == source.get("metadata", {}).get("model"),
        "judge_temperature": settings.llm_temperature,
        "source_report_sha256": hashlib.sha256(raw_source).hexdigest(),
        "dataset_sha256": dataset_digest(cases), "expected_observations": len(cases),
        "independent_rule_source_sha256": hashlib.sha256((ROOT / "app/indexing/rule_document_source.py").read_bytes()).hexdigest(),
        "semantic_module_sha256": hashlib.sha256((ROOT / "evaluation/semantic_evaluation.py").read_bytes()).hexdigest(),
        "cost_status": "not_estimated_without_verified_provider_pricing",
    }
    rows, details = [], {}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    secrets = (settings.llm_api_key, settings.langsmith_api_key)

    def save(final=False):
        report = summarize_semantics(rows)
        report.update(metadata=metadata, final=final, **details)
        report["source_fallback_answers"] = sum(row["source_fallback"] for row in rows)
        report["source_non_model_answers"] = sum(not row["source_model_generated"] for row in rows)
        report["judge_provider_tokens"] = {
            key: sum(row["judge_usage"]["tokens"].get(key, 0) for row in rows)
            for key in {key for row in rows for key in row["judge_usage"]["tokens"]}}
        report["judge_usage_observed_rows"] = sum(bool(row["judge_usage"]["tokens"]) for row in rows)
        args.output.write_text(json.dumps(redact(report, secrets=secrets), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        return report

    def target(inputs):
        with observation_scope(settings, "evaluation.answer_semantics.advisory", inputs=inputs,
                               metadata=metadata, enabled=args.publish) as run:
            output = run_judgment(inputs, settings)
            finish_trace(run, output)
        rows.append(output)
        save()
        print(json.dumps({"case": output["case_id"], "trial": output["trial"], "status": output["status"],
                          "scores": output["scores"], "error": output.get("error")}), flush=True)
        return output

    save()
    try:
        if args.publish:
            client = client_for(settings)
            name = sync_dataset(client, args.dataset_prefix, "answer-semantics-advisory", cases)
            experiment = client.evaluate(target, data=name, evaluators=[semantic_evaluator],
                experiment_prefix=args.experiment_prefix, metadata=metadata, max_concurrency=1, blocking=True)
            evaluated = list(experiment)
            project = client.read_project(project_name=experiment.experiment_name)
            details.update(dataset_name=name, experiment_name=experiment.experiment_name,
                           experiment_id=str(project.id), experiment_url=project.url, evaluated_rows=len(evaluated))
        else:
            for case in cases:
                target(case["inputs"])
        report = save(final=True)
        print(json.dumps({key: report[key] for key in ("observations", "evaluated", "source_unavailable", "judge_unavailable", "metrics")}), flush=True)
        # Scores are advisory; low quality does not impersonate a transaction gate.
        return int(len(rows) != len(cases) or report["judge_unavailable"] > 0 or report["source_unavailable"] > 0)
    finally:
        close_langsmith()


if __name__ == "__main__":
    raise SystemExit(main())
