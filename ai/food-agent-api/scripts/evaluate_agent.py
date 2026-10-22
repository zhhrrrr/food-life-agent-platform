"""Run isolated Planner/RAG experiments locally or publish them to LangSmith."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.core.config import get_settings
from app.core.langsmith import client_for, close_langsmith
from evaluation.agent_evaluation import (
    DEFAULT_CASES, dataset_digest, load_examples, make_target, planner_evaluator,
    planner_scores, rag_evaluator, rag_scores, summarize, sync_dataset,
)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kind", choices=("planner", "rag"), default="planner")
    parser.add_argument("--cases", type=Path)
    parser.add_argument("--publish", action="store_true", help="Upload versioned dataset and run a LangSmith experiment")
    parser.add_argument("--rules", action="store_true", help="Planner diagnostic baseline; never counted as LLM success")
    parser.add_argument("--dataset-prefix", default="food-agent")
    parser.add_argument("--experiment-prefix", default="food-agent")
    parser.add_argument("--revision", help="Prompt/code version label; defaults to LANGSMITH_REVISION")
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--max-concurrency", type=int, default=1)
    parser.add_argument("--min-score", type=float, default=0.9, help="Planner strict success or RAG hit/recall gate")
    parser.add_argument("--min-mrr", type=float, default=0.8)
    parser.add_argument("--min-ndcg", type=float, default=0.85)
    parser.add_argument("--min-rejection-rate", type=float, default=1.0)
    parser.add_argument("--max-p95-ms", type=float, default=60000)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    if not 1 <= args.max_concurrency <= 4 or not 1 <= args.top_k <= 50 or args.max_p95_ms <= 0:
        parser.error("concurrency must be 1..4, top-k 1..50, max-p95-ms > 0")
    if any(not 0 <= value <= 1 for value in (args.min_score, args.min_mrr, args.min_ndcg, args.min_rejection_rate)):
        parser.error("score gates must be between 0 and 1")
    settings = get_settings()
    if args.revision is not None:
        settings = settings.model_copy(update={"langsmith_revision": args.revision})
    if args.rules:
        if args.kind != "planner":
            parser.error("--rules applies only to Planner")
        settings = settings.model_copy(update={"llm_router_enabled": False})
    elif args.kind == "planner" and (not settings.llm_router_enabled or not settings.llm_api_key):
        parser.error("Planner model evaluation needs LLM_ROUTER_ENABLED=true and LLM_API_KEY; use --rules for a diagnostic baseline")
    if args.publish and not settings.langsmith_api_key.strip():
        parser.error("--publish requires a separate LANGSMITH_API_KEY in .env or environment")
    try:
        cases = load_examples(args.kind, args.cases or DEFAULT_CASES[args.kind])
        target = make_target(args.kind, settings, publish=args.publish, top_k=args.top_k)
        score = planner_scores if args.kind == "planner" else rag_scores
        metadata = {"app_version": settings.app_version, "revision": settings.langsmith_revision,
                    "models": [settings.llm_model if args.kind == "planner" else settings.embedding_model],
                    "dataset_sha256": dataset_digest(cases), "rules_baseline": args.rules,
                    "kind": args.kind, "top_k": args.top_k, "environment": settings.langsmith_environment}
        if args.publish:
            client = client_for(settings)
            dataset_name = sync_dataset(client, args.dataset_prefix, args.kind, cases)
            experiment = client.evaluate(target, data=dataset_name,
                evaluators=[planner_evaluator if args.kind == "planner" else rag_evaluator],
                experiment_prefix=args.experiment_prefix, metadata=metadata,
                max_concurrency=args.max_concurrency, blocking=True)
            rows = [{"id": item["example"].metadata["case_id"], "outputs": item["run"].outputs,
                     "scores": score(item["run"].outputs, item["example"].outputs)} for item in experiment]
            report = summarize(args.kind, rows)
            report.update(dataset_name=dataset_name, experiment_name=experiment.experiment_name)
        else:
            rows = []
            for case in cases:
                outputs = target(case["inputs"])
                rows.append({"id": case["id"], "outputs": outputs, "scores": score(outputs, case["outputs"])})
            report = summarize(args.kind, rows)
        report["metadata"] = metadata
        scores = report["scores"]
        passed = report["errors"] == 0 and report["latency_p95_ms"] <= args.max_p95_ms
        if args.kind == "planner":
            passed = passed and scores["strict_success"] >= args.min_score
        else:
            passed = passed and all(scores.get(key, 0) >= threshold for key, threshold in (
                ("hit_rate_at_k", args.min_score), ("recall_at_k", args.min_score), ("mrr_at_k", args.min_mrr),
                ("ndcg_at_k", args.min_ndcg), ("rejection_success", args.min_rejection_rate)))
        report["passed"] = bool(passed)
        text = json.dumps(report, ensure_ascii=False, indent=2)
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(text + "\n", encoding="utf-8")
        print(text)
        return 0 if passed else 1
    finally:
        close_langsmith()


if __name__ == "__main__":
    raise SystemExit(main())
