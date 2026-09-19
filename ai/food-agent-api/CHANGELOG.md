# Changelog

## 0.7.0

- Added the stage 4 lightweight RAG retrieval chain for RecommendAgent and SupportAgent.
- Introduced a reusable `RagRetriever` result object and structured context item builder on top of ES BM25 search.
- Changed RecommendAgent and SupportAgent from pure mock replies to evidence-aware template answers with references.
- Documented that this stage is Retrieval + Template Answer, not full LLM RAG.

## 0.6.3

- Extracted Redis distributed lock logic from the ES reconciliation scheduler into a reusable sync lock module.
- Added focused tests for disabled-lock behavior, TTL normalization, and reconciliation lock settings mapping.

## 0.6.2

- Added an optional Redis distributed lock for ES reconciliation so only one FastAPI/ASGI worker runs each reconciliation round.
- Added lock renewal and token-checked Lua release to avoid stale lock expiry and accidental unlock by another process.

## 0.6.1

- Hardened the single-instance ES reconciliation scheduler with duplicate-start protection, optional run-on-startup, graceful stop, and interval normalization.

## 0.6.0

- Added paged Java search export support for full ES reconciliation snapshots.
- Added manual and scheduled ES reconciliation to repair missed events and stale documents.
- Added ES read/ID-scan helpers so reconciliation can skip unchanged documents and prune old ones.
- Added reconciliation configuration for interval, page size, stale pruning, and scan size.

## 0.5.0

- Added reliable incremental ES sync support through RabbitMQ search sync consumers.
- Added single-document Java export support so ES events update or delete one document instead of rebuilding whole indexes.
- Added ES delete-on-miss behavior for decremental sync when Java returns no active search document.
- Added dedicated business/trade search sync queue configuration to avoid competing with business consumers.

## 0.4.0

- Added Java export client support for business and trade search documents.
- Added index rebuild API for shops, meal packages, shop reviews, activities, and coupons.
- Added ES index configuration for review, activity, and coupon documents.
- Kept platform rules and FAQ documents out of the MySQL export path; they should come from formal Markdown or configuration documents later.

## 0.3.0

- Moved `food-agent-api` into the Spring application Git repository.
- Added an Elasticsearch search layer with optional runtime enablement.
- Added search schemas, index-name helpers, and document indexing entrypoint.
- Added a lightweight RAG retrieval/context path for RecommendAgent and SupportAgent.
- Extended chat responses with `references` while preserving `intent`, `agent`, and `response`.

## 0.2.0

- Added application settings loaded from environment variables and `.env`.
- Added request ID middleware and structured service logs.
- Added stable JSON error responses for agent failures.
- Kept the current rule-based Router as the stage 1 fallback.

## 0.1.0

- Added FastAPI entrypoints.
- Added LangGraph state, router, and three mock agent nodes.
- Added basic pytest coverage.
