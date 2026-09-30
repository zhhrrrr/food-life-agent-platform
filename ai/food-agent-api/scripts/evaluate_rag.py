import argparse
import json
import math
import sys
import time
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.rag.retriever import RagRetriever


def load_cases(path: Path) -> list[dict[str, Any]]:
    return [
        json.loads(line)
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def _ndcg_at_k(
    actual_ids: list[str],
    expected_ids: set[str],
    top_k: int,
    *,
    match_any: bool = False,
) -> float:
    gains = [1.0 if document_id in expected_ids else 0.0 for document_id in actual_ids[:top_k]]
    if match_any:
        first_relevant = next((rank for rank, gain in enumerate(gains, start=1) if gain), None)
        return 0.0 if first_relevant is None else 1.0 / math.log2(first_relevant + 1)
    dcg = sum(gain / math.log2(rank + 1) for rank, gain in enumerate(gains, start=1))
    ideal_count = min(len(expected_ids), top_k)
    if ideal_count == 0:
        return 0.0
    ideal_dcg = sum(1.0 / math.log2(rank + 1) for rank in range(1, ideal_count + 1))
    return dcg / ideal_dcg


def _percentile(values: list[float], percentile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    rank = max(1, math.ceil(percentile * len(ordered)))
    return ordered[rank - 1]


def evaluate(path: Path, top_k: int) -> dict[str, Any]:
    retriever = RagRetriever()
    results: list[dict[str, Any]] = []
    for case in load_cases(path):
        started_at = time.perf_counter()
        retrieval = retriever.retrieve(
            case["agent"],
            case["query"],
            size=top_k,
            original_query=case["query"],
            capabilities=case.get("capabilities"),
        )
        duration_ms = (time.perf_counter() - started_at) * 1000
        actual_ids = [hit.id for hit in retrieval.hits]
        should_reject = bool(case.get("should_reject", False))
        expected_ids = set(case["expected_ids"])
        match_any = case.get("relevance_mode") == "any"
        if should_reject:
            recall = None
            reciprocal_rank = None
            ndcg = None
            passed = not actual_ids
        else:
            matched_ids = expected_ids.intersection(actual_ids)
            recall = (
                float(bool(matched_ids))
                if match_any
                else len(matched_ids) / len(expected_ids)
                if expected_ids
                else 0.0
            )
            relevant_ranks = [
                rank
                for rank, document_id in enumerate(actual_ids, start=1)
                if document_id in expected_ids
            ]
            reciprocal_rank = 1.0 / min(relevant_ranks) if relevant_ranks else 0.0
            ndcg = _ndcg_at_k(actual_ids, expected_ids, top_k, match_any=match_any)
            passed = bool(matched_ids)
        results.append(
            {
                "id": case["id"],
                "passed": passed,
                "should_reject": should_reject,
                "mode": retrieval.mode,
                "recall": recall,
                "reciprocal_rank": reciprocal_rank,
                "ndcg": ndcg,
                "duration_ms": round(duration_ms, 2),
                "actual_ids": actual_ids,
            }
        )

    positive_results = [result for result in results if not result["should_reject"]]
    rejection_results = [result for result in results if result["should_reject"]]
    positive_passed = sum(1 for result in positive_results if result["passed"])
    rejected = sum(1 for result in rejection_results if result["passed"])
    passed_count = positive_passed + rejected
    mean_recall = (
        sum(float(result["recall"]) for result in positive_results)
        / len(positive_results)
        if positive_results
        else 0.0
    )
    mean_reciprocal_rank = (
        sum(float(result["reciprocal_rank"]) for result in positive_results)
        / len(positive_results)
        if positive_results
        else 0.0
    )
    mean_ndcg = (
        sum(float(result["ndcg"]) for result in positive_results)
        / len(positive_results)
        if positive_results
        else 0.0
    )
    latencies = [float(result["duration_ms"]) for result in results]
    return {
        "cases": len(results),
        "passed": passed_count,
        "pass_rate": passed_count / len(results) if results else 0.0,
        "positive_cases": len(positive_results),
        "positive_passed": positive_passed,
        "hit_rate_at_k": (
            positive_passed / len(positive_results) if positive_results else 0.0
        ),
        "recall_at_k": mean_recall,
        "mrr_at_k": mean_reciprocal_rank,
        "ndcg_at_k": mean_ndcg,
        "rejection_cases": len(rejection_results),
        "rejected": rejected,
        "rejection_rate": (
            rejected / len(rejection_results) if rejection_results else 1.0
        ),
        "latency_mean_ms": sum(latencies) / len(latencies) if latencies else 0.0,
        "latency_p95_ms": _percentile(latencies, 0.95),
        "top_k": top_k,
        "results": results,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate deterministic RAG retrieval quality.")
    parser.add_argument(
        "--cases",
        type=Path,
        default=ROOT / "evals" / "rag_retrieval_cases.jsonl",
    )
    parser.add_argument("--top-k", type=int, default=5)
    parser.add_argument("--min-hit-rate", type=float, default=0.9)
    parser.add_argument("--min-recall", type=float, default=0.9)
    parser.add_argument("--min-mrr", type=float, default=0.8)
    parser.add_argument("--min-ndcg", type=float, default=0.85)
    parser.add_argument("--min-rejection-rate", type=float, default=1.0)
    parser.add_argument("--max-p95-ms", type=float, default=3000.0)
    args = parser.parse_args()

    report = evaluate(args.cases, top_k=args.top_k)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return (
        0
        if report["hit_rate_at_k"] >= args.min_hit_rate
        and report["recall_at_k"] >= args.min_recall
        and report["mrr_at_k"] >= args.min_mrr
        and report["ndcg_at_k"] >= args.min_ndcg
        and report["rejection_rate"] >= args.min_rejection_rate
        and report["latency_p95_ms"] <= args.max_p95_ms
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
