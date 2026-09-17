# Changelog

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
