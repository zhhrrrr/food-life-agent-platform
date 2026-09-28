import argparse
import json
import sys
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


def evaluate(path: Path, top_k: int) -> dict[str, Any]:
    retriever = RagRetriever()
    results: list[dict[str, Any]] = []
    for case in load_cases(path):
        retrieval = retriever.retrieve(
            case["agent"],
            case["query"],
            size=top_k,
            original_query=case["query"],
            capabilities=case.get("capabilities"),
        )
        actual_ids = [hit.id for hit in retrieval.hits]
        should_reject = bool(case.get("should_reject", False))
        expected_ids = set(case["expected_ids"])
        if should_reject:
            recall = None
            reciprocal_rank = None
            passed = not actual_ids
        else:
            matched_ids = expected_ids.intersection(actual_ids)
            recall = len(matched_ids) / len(expected_ids) if expected_ids else 0.0
            relevant_ranks = [
                rank
                for rank, document_id in enumerate(actual_ids, start=1)
                if document_id in expected_ids
            ]
            reciprocal_rank = 1.0 / min(relevant_ranks) if relevant_ranks else 0.0
            passed = bool(matched_ids)
        results.append(
            {
                "id": case["id"],
                "passed": passed,
                "should_reject": should_reject,
                "mode": retrieval.mode,
                "recall": recall,
                "reciprocal_rank": reciprocal_rank,
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
        "rejection_cases": len(rejection_results),
        "rejected": rejected,
        "rejection_rate": (
            rejected / len(rejection_results) if rejection_results else 1.0
        ),
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
    parser.add_argument("--min-recall", type=float, default=0.9)
    parser.add_argument("--min-rejection-rate", type=float, default=1.0)
    args = parser.parse_args()

    report = evaluate(args.cases, top_k=args.top_k)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return (
        0
        if report["recall_at_k"] >= args.min_recall
        and report["rejection_rate"] >= args.min_rejection_rate
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
