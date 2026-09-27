import argparse
from collections import Counter
from dataclasses import dataclass

from app.core.config import get_settings
from app.graph.route_examples import ROUTE_EXAMPLES, SemanticIntent
from app.graph.semantic_router import (
    build_semantic_query,
    cosine_similarity,
    score_semantic_route,
)
from app.llm.embedding_model import build_embedding_model
from app.memory.conversation_memory import ConversationTurn
from evaluation.semantic_route_cases import (
    SEMANTIC_ROUTE_EVALUATION_CASES,
    SemanticRouteEvaluationCase,
)


@dataclass(frozen=True, slots=True)
class ScoredCase:
    expected: SemanticIntent
    predicted: SemanticIntent
    score: float
    margin: float
    message: str


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Evaluate semantic route quality with the configured embedding model."
    )
    parser.add_argument("--min-accuracy", type=float, default=0.90)
    parser.add_argument("--min-accepted-precision", type=float, default=0.98)
    args = parser.parse_args()

    settings = get_settings()
    if not settings.semantic_router_enabled:
        parser.error("SEMANTIC_ROUTER_ENABLED must be true")

    model = build_embedding_model(settings)
    example_vectors = model.embed_documents(
        [example.text for example in ROUTE_EXAMPLES]
    )
    query_texts = [_evaluation_query(case) for case in SEMANTIC_ROUTE_EVALUATION_CASES]
    query_vectors = model.embed_documents(query_texts)

    scored_cases: list[ScoredCase] = []
    for case, query_vector in zip(
        SEMANTIC_ROUTE_EVALUATION_CASES, query_vectors, strict=True
    ):
        match = score_semantic_route(
            [
                (example, cosine_similarity(query_vector, example_vector))
                for example, example_vector in zip(
                    ROUTE_EXAMPLES, example_vectors, strict=True
                )
            ],
            high_threshold=0.0,
            min_margin=0.0,
        )
        scored_cases.append(
            ScoredCase(
                expected=case.intent,
                predicted=match.intent,
                score=match.score,
                margin=match.margin,
                message=case.message,
            )
        )

    accuracy = _accuracy(scored_cases)
    configured = _gate_metrics(
        scored_cases,
        settings.semantic_router_high_threshold,
        settings.semantic_router_min_margin,
    )
    calibrated = _best_gate(scored_cases, args.min_accepted_precision)
    confusion = Counter((case.expected, case.predicted) for case in scored_cases)

    print(f"embedding_model={settings.embedding_model}")
    print(f"route_examples={len(ROUTE_EXAMPLES)} evaluation_cases={len(scored_cases)}")
    print(f"top1_accuracy={accuracy:.4f}")
    print(
        "configured_gate="
        f"score>={settings.semantic_router_high_threshold:.2f},"
        f"margin>={settings.semantic_router_min_margin:.2f},"
        f"accepted_precision={configured[0]:.4f},coverage={configured[1]:.4f},"
        f"accepted={configured[2]}"
    )
    if calibrated is not None:
        threshold, margin, precision, coverage, accepted = calibrated
        print(
            "calibrated_candidate="
            f"score>={threshold:.2f},margin>={margin:.2f},"
            f"accepted_precision={precision:.4f},coverage={coverage:.4f},accepted={accepted}"
        )
    print("confusion_matrix:")
    for expected in ("recommend", "trade", "support"):
        row = " ".join(
            f"{predicted}={confusion[(expected, predicted)]}"
            for predicted in ("recommend", "trade", "support")
        )
        print(f"  expected_{expected}: {row}")

    errors = [case for case in scored_cases if case.expected != case.predicted]
    if errors:
        print("top1_errors:")
        for case in errors:
            print(
                f"  expected={case.expected} predicted={case.predicted} "
                f"score={case.score:.4f} margin={case.margin:.4f} message={case.message}"
            )

    if accuracy < args.min_accuracy:
        return 1
    if configured[2] == 0 or configured[0] < args.min_accepted_precision:
        return 1
    return 0


def _evaluation_query(case: SemanticRouteEvaluationCase) -> str:
    history = []
    if case.previous_user:
        history.append(
            ConversationTurn(
                user=case.previous_user,
                intent=case.previous_intent,
            )
        )
    return build_semantic_query(case.message, history)


def _accuracy(cases: list[ScoredCase]) -> float:
    return sum(case.expected == case.predicted for case in cases) / len(cases)


def _gate_metrics(
    cases: list[ScoredCase],
    threshold: float,
    margin: float,
) -> tuple[float, float, int]:
    accepted = [
        case for case in cases if case.score >= threshold and case.margin >= margin
    ]
    if not accepted:
        return 0.0, 0.0, 0
    precision = sum(case.expected == case.predicted for case in accepted) / len(
        accepted
    )
    return precision, len(accepted) / len(cases), len(accepted)


def _best_gate(
    cases: list[ScoredCase],
    min_precision: float,
) -> tuple[float, float, float, float, int] | None:
    candidates = []
    for threshold_step in range(70, 91):
        for margin_step in range(2, 16):
            threshold = threshold_step / 100
            margin = margin_step / 100
            precision, coverage, accepted = _gate_metrics(cases, threshold, margin)
            if accepted and precision >= min_precision:
                candidates.append((threshold, margin, precision, coverage, accepted))
    if not candidates:
        return None
    return max(candidates, key=lambda item: (item[3], item[2], -item[0], -item[1]))


if __name__ == "__main__":
    raise SystemExit(main())
