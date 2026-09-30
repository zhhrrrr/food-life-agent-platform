# Changelog

## 0.27.0

- Added authenticated internal headers to Java search-export reads and allowed Trade export endpoints through service authentication.
- Changed Milvus rebuild embedding and writes to bounded streaming batches and completed vector coverage for all recommendation projections.
- Added parallel runtime readiness probes for Java Gateway, Elasticsearch, Milvus, Redis, and RabbitMQ.
- Replaced obsolete demo retrieval cases with current business IDs and added Hit Rate, Recall, MRR, NDCG, rejection, and P95 latency gates.
- Enabled local RabbitMQ incremental search sync and Redis-locked scheduled reconciliation.

## 0.26.0

- Added bounded recent-turn context for genuine follow-up query rewriting across all three Agents.
- Kept conversation text untrusted and limited it to reference resolution rather than business facts.
- Added ordinal package/order ID resolution from typed artifacts and recent turns for TradeAgent.
- Prevented explicit new topics from being contaminated by unrelated session history.

## 0.25.0

- Routed Agent traffic through Spring Cloud Gateway and added a trusted identity-secret boundary.
- Protected manual index rebuild and reconciliation endpoints with an independent admin token.
- Added liveness, configuration-readiness, HTTP/chat/tool Prometheus metrics, and `/metrics`.
- Made the Python test suite independent from active local LLM, ES, Milvus, Redis, and MQ settings.

## 0.24.0

- Added an optional MCP 2.x Streamable HTTP endpoint mounted into the existing FastAPI app.
- Reused the native read-only Trade Tool Registry and Executor instead of duplicating business calls.
- Required a constant-time checked internal MCP token and kept credentials out of tool schemas.
- Added read-only MCP annotations, stateless transport, and configurable Host/Origin allowlists.

## 0.23.0

- Added a native Tool Registry and bounded Tool Executor with strict Pydantic arguments.
- Registered only allow-listed TradeAgent reads; no transaction mutation tool exists.
- Added one LLM Tool Calling selection round with deterministic fallback and explicit-ID protection.
- Enforced tool ownership, authorization, deduplication, and maximum-call boundaries before Java access.

## 0.22.0

- Reduced orchestration to one real capability per Agent and removed duplicate pseudo-stages.
- Added typed task artifacts for IDs, facts, and references passed between dependent Agents.
- Stopped injecting serialized upstream payloads into user prompts and query parsers.
- Allowed TradeAgent and SupportAgent to consume verified upstream artifacts directly.

## 0.21.0

- Replaced `conversation_id` with `session_id` across the public API and orchestration state.
- Added durable MySQL sessions and raw turns, optimistic historical summaries, and Redis hot session views.
- Added structured long-term preferences in MySQL and isolated per-user semantic memory in Milvus.
- Added deterministic memory routing so recommendation, support-history, and trade queries use only appropriate memory sources.
- Added transactional Outbox delivery, RabbitMQ retry, dead-letter storage, idempotent Milvus sync, and 180-day semantic-event expiry.

## 0.20.1

- Removed the obsolete three-way Tool Calling router after dynamic planning became the only production orchestration entry point.
- Preserved Semantic Router coverage directly against DynamicPlanner and removed tests for the retired path.
- Prevented generic order-status explanations from triggering unnecessary TradeAgent queries while retaining concrete order lookups.
- Enforced each DAG task's own timeout instead of applying the longest timeout to the entire parallel wave.
- Reused one plan-agent calculation during response composition and removed the unnecessary lazy GeneralAgent wrapper.
- Replaced deprecated FastAPI startup and shutdown decorators with one lifespan context.

## 0.20.0

- Replaced the three-way LangGraph branch with a fixed orchestration control graph that executes validated runtime task DAGs.
- Added Semantic Router single-task fast paths and one-call LLM structured planning for uncertain or compound requests.
- Added strict capability ownership, missing-dependency, cycle, depth, task-count, repeated-agent, and GeneralAgent isolation validation.
- Added dependency-aware parallel execution, bounded retries and timeouts, failed-branch skipping, partial results, and one bounded replan.
- Added structured upstream result propagation so downstream agents can consume verified IDs, facts, and references.
- Added a tool-free GeneralAgent for greetings, clarification, capability guidance, and out-of-domain fallback.
- Extended chat responses with run, plan revision, workflow, participating agents, execution status, and per-task status metadata.
- Kept TradeAgent on its existing read-only Java Gateway capability without adding transaction actions.

## 0.19.3

- Raised the final reranker evidence floor to `0.2` after positive and out-of-domain score calibration.
- Added negative retrieval cases and an explicit rejection-rate release gate to the RAG evaluator.
- Kept low-confidence retrieval on the existing no-evidence response path instead of generating an unsupported answer.

## 0.19.2

- Added a separate local dependency profile for running Milvus Lite as a Windows gRPC development service.
- Documented the configuration boundary between local Milvus Lite and production Milvus Standalone or Distributed.
- Added a local-only explicit-flush switch to avoid unsupported blocking flush-state polling in the Windows Milvus Lite gRPC server.
- Replaced raw evidence-block fallback responses with bounded, user-facing evidence summaries.
- Distinguished template fallbacks from successful LLM answers in RecommendAgent and SupportAgent `rag_mode` values.
- Added load-state recovery before Milvus search and reconciliation reads so aliases recover after a vector-service restart.

## 0.19.1

- Unified retrieval scope selection so Elasticsearch indexes and Milvus collections are derived from one business-scope decision.
- Removed unused single-index search and skipped RRF when only one retrieval channel returns candidates.
- Unified prompt context, API context, and references so truncated evidence can never be cited.
- Shared grounded-answer invocation and reference validation across RecommendAgent, SupportAgent, and TradeAgent.
- Added configurable vector and reranker relevance floors to reject low-confidence evidence.
- Reused ChatModel, EmbeddingModel, and reranker HTTP clients instead of rebuilding connection pools per request.
- Updated RAG prompts and fallback messages to describe hybrid retrieval instead of an Elasticsearch-only path.

## 0.19.0

- Moved dense vectors and cosine retrieval out of Elasticsearch into independent Milvus collections.
- Added versioned Milvus rebuild collections with validated counts, preloaded alias promotion, rollback, and bounded retention.
- Extended full rebuild, RabbitMQ incremental sync, delete handling, and scheduled reconciliation to maintain both Elasticsearch and Milvus.
- Added content hashes so reconciliation repairs stale vectors, not only missing vector IDs.
- Kept Elasticsearch for BM25 and structured filters, then fused ES and Milvus candidates with RRF before reranking.
- Added a Milvus Standalone Docker Compose deployment and BM25 degradation when Milvus is disabled or unavailable.

## 0.18.0

- Added BGE-M3 document/query embeddings and Elasticsearch 7.17 `dense_vector` cosine retrieval.
- Added parallel BM25 and vector candidate channels with application-level reciprocal rank fusion.
- Added SiliconFlow BGE reranking with validated result indices and BM25/RRF degradation on provider failure.
- Added configurable candidate sizing, embedding dimensions/batches, RRF rank constant, and reranker provider settings.
- Exposed the executed retrieval mode in agent state and expanded evaluation with hit rate, recall, and MRR.

## 0.17.0

- Added strict per-scope Elasticsearch mappings with CJK text analysis and typed business metadata.
- Replaced in-place rebuilds with paged export, Bulk indexing, count validation, atomic write-alias promotion, and bounded rollback index retention.
- Replaced per-index search loops with one DFS multi-index request that combines original and rewritten queries, active-time filtering, status filtering, and validated budget constraints.
- Made recommendation capabilities select relevant indexes and diversified candidate evidence across document sources.
- Added source-specific context metadata allowlists, context size limits, untrusted-evidence prompting, and strict LLM reference ID validation.
- Replaced the reconciliation ID scan limit with PIT and search-after pagination.

## 0.16.1

- Forced Java export and Trade Gateway HTTP calls to bypass environment proxies so loopback and private service addresses are reached directly.
- Limited BM25 multi-match queries to text fields so numeric metadata cannot make Elasticsearch reject natural-language searches.

## 0.16.0

- Reduced Elasticsearch business documents to search-oriented public projections: package and activity stock plus coupon supply counters are no longer indexed.
- Removed stock-only activity and coupon ES sync publications from transactional order and coupon paths, including their redundant database lookup and helper code.
- Kept activity and coupon indexes for recommendation discovery while preserving Java Gateway as the only source for current stock, eligibility, user coupons, teams, and orders.
- Removed the unused Elasticsearch index-name identity mapper.

## 0.15.0

- Removed SupportAgent's duplicate Java order-status client; concrete order reads now belong exclusively to TradeAgent through Java Gateway, while SupportAgent remains an ES-backed rule and FAQ chain.
- Centralized the repeated Structured Output enablement, exception handling, logging, and fallback behavior shared by RecommendAgent, SupportAgent, and TradeAgent.
- Removed empty agent-specific answer/result models that added no fields or validation and reused the shared chain contracts directly.
- Centralized transaction-mutation detection so Router and TradeAgent enforce one identical create/cancel/pay/refund policy.
- Strengthened deterministic routing so current or concrete order queries go to TradeAgent while generic status meanings remain with SupportAgent.

## 0.14.0

- Expanded the semantic route corpus from 25 demo phrases to 138 validated anchors grouped by intent, capability, and difficult routing boundary.
- Added a separate balanced 60-case evaluation suite with contextual follow-ups and a live embedding quality gate for accuracy, accepted precision, coverage, and confusion analysis.
- Disabled incompatible client-side OpenAI tokenization so SiliconFlow BGE-M3 receives original Chinese text instead of provider-unsafe token ID arrays.
- Verified BGE-M3 at 96.67% top-1 accuracy and 100% accepted-route precision with 76.67% coverage under the existing 0.74 score and 0.05 margin gates.

## 0.13.2

- Removed unused stage-4 RAG compatibility aliases and the test-only retrieval wrapper.
- Simplified LangGraph state initialization to contain only actual request inputs.
- Removed duplicated route types and unconsumed router metadata from graph state.
- Removed the unused `langchain` meta-package while retaining `langchain-openai`, `langchain-core`, and LangGraph transitively.
- Removed redundant conversation-memory comments without changing Redis behavior.

## 0.13.1

- Added an explicit SiliconFlow embedding provider with the official OpenAI-compatible base URL.
- Expanded canonical route examples for recommendation comparisons, real-time trade facts, coupons, and support-rule boundaries.
- Calibrated the BAAI/bge-m3 semantic acceptance gate to a 0.74 similarity threshold while retaining a 0.05 top-intent margin.
- Verified a real 1024-dimensional SiliconFlow embedding response and kept ambiguous semantic matches on the LLM fallback path.

## 0.13.0

- Added bounded, expiring Redis conversation memory shared across FastAPI workers without storing Authorization.
- Added a cached embedding-based Semantic Router with configurable high-confidence and top-intent margin gates.
- Added LLM Function Calling through three restricted routing tools: `route_recommend`, `route_trade`, and `route_support`.
- Kept Structured Output and deterministic rules as ordered fallbacks when semantic routing is uncertain or providers fail.
- Added router source/confidence and conversation ID response metadata for routing observability.
- Isolated embedding credentials from LLM credentials and left semantic routing disabled until an embedding API is configured and evaluated.

## 0.12.0

- Upgraded TradeAgent from a mock to read-only price, stock, coupon, group-buy, order, and order-preview queries through Java Gateway.
- Added an allow-listed GET-only Gateway client that forwards user Authorization but exposes no create, cancel, pay, refund, operations, or generic write API.
- Built order previews from the package trade snapshot and current-user coupons without creating an order or changing inventory.
- Unified RecommendAgent, SupportAgent, and TradeAgent around shared query, answer, result, capability merge, fallback, and reference-selection contracts.
- Added deterministic identifier extraction and transaction-action blocking so LLM output cannot invent resource IDs or authorize writes.

## 0.11.1

- Unified RecommendAgent and SupportAgent around the same rewrite -> retrieval -> context -> structured answer chain contract.
- Added shared query, answer, result, capability merge, reference selection, and LangChain structured-output helpers.
- Required non-blank rewritten queries and at least one valid capability; invalid LLM plans now fall back to deterministic local plans.
- Added SupportAgent query rewriting while keeping Java order lookup gated by explicit local order-status wording and an order ID.
- Filtered response references by structured `used_reference_ids`, with all verified evidence retained when the model returns no valid IDs.

## 0.11.0

- Upgraded SupportAgent from a retrieval template to a rule retrieval -> optional read-only Java order-status query -> LLM answer chain.
- Added structured support answers for refund rules, group-buy rules, payment rules, order-status explanations, and platform FAQ.
- Forwarded the incoming Authorization header only for Java's owner-authorized order-detail query; SupportAgent never accesses MySQL, operations endpoints, or order write APIs.
- Preserved evidence references and template fallback when the LLM, ES, Java service, authorization, or order ID is unavailable.

## 0.10.0

- Upgraded RecommendAgent from a retrieval template to a query rewrite -> ES/RAG retrieval -> LLM answer chain.
- Added recommendation capabilities for shops, packages, cuisine filters, budget filters, scenes, reviews, and shop comparison.
- Kept recommendation read-only: the agent does not create orders, reserve stock, or mutate business data.
- Preserved template fallback when LLM or ES evidence is unavailable.

## 0.9.2

- Switched the Zhipu LangChain structured router call to JSON mode for OpenAI-compatible response parsing.
- Strengthened the router prompt to require a complete JSON object with `intent`, `confidence`, and `reason`.
- Verified real `glm-4.5-air` routing returns an `IntentResult` instead of falling back to rules.

## 0.9.1

- Added `zhipu` as an OpenAI-compatible LangChain chat model provider.
- Added the default Zhipu GLM base URL for `LLM_PROVIDER=zhipu`.
- Documented the `glm-4.5-air` router configuration while keeping real API keys out of committed files.
- Added tests for Zhipu chat model option resolution.

## 0.9.0

- Added LangChain ChatModel configuration for the intent router.
- Added `IntentResult` Pydantic Structured Output with intent, confidence, and reason.
- Replaced the LangGraph router node with an LLM router while preserving the rule-based router as fallback.
- Added tests for fallback routing, router metadata, and structured LLM routing.

## 0.8.0

- Added a seeded platform rule document source for SupportAgent RAG retrieval.
- Added the `rules` search scope, mapped to `food_rule_index`, while keeping MySQL-backed scopes exported by Java services.
- Extended rebuild/reconcile indexing so rules use the same `SearchDocument` schema and ES write path as business documents.
- Documented the dedicated rule rebuild command and the distinction between MySQL business documents and non-MySQL platform rule documents.

## 0.7.1

- Simplified scheduled ES reconciliation locking by removing local `_start_lock` and `_run_lock`.
- Kept only the Redis distributed lock as the run guard for multi-instance reconciliation reliability.
- Documented the timer-thread -> Redis lock -> reconcile -> release flow.

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
