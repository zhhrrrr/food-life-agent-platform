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
        expected_ids = set(case["expected_ids"])
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
                "mode": retrieval.mode,
                "recall": recall,
                "reciprocal_rank": reciprocal_rank,
                "actual_ids": actual_ids,
            }
        )

    passed_count = sum(1 for result in results if result["passed"])
    mean_recall = (
        sum(float(result["recall"]) for result in results) / len(results)
        if results
        else 0.0
    )
    mean_reciprocal_rank = (
        sum(float(result["reciprocal_rank"]) for result in results) / len(results)
        if results
        else 0.0
    )
    return {
        "cases": len(results),
        "passed": passed_count,
        "hit_rate_at_k": passed_count / len(results) if results else 0.0,
        "recall_at_k": mean_recall,
        "mrr_at_k": mean_reciprocal_rank,
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
    args = parser.parse_args()

    report = evaluate(args.cases, top_k=args.top_k)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["recall_at_k"] >= args.min_recall else 1


if __name__ == "__main__":
    raise SystemExit(main())
