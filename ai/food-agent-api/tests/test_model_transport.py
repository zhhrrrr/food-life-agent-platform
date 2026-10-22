"""Exercise provider adapters over real HTTP with an unavailable system proxy."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Thread

import pytest

from app.core.config import Settings
from app.llm.chat_model import build_chat_model
from app.llm.embedding_model import build_embedding_model
from app.rag.reranker import RagReranker
from app.search.schemas import SearchHit


class Provider(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", 0)))
        if self.path.endswith("/embeddings"):
            result = {"object": "list", "data": [{"object": "embedding", "index": 0, "embedding": [1.0, 0.5]}],
                      "model": "test-model", "usage": {"prompt_tokens": 1, "total_tokens": 1}}
        elif self.path.endswith("/rerank"):
            result = {"results": [{"index": 0, "relevance_score": 0.9}]}
        else:
            result = {"id": "test", "object": "chat.completion", "created": 1, "model": "test-model",
                      "choices": [{"index": 0, "message": {"role": "assistant", "content": "ok"}, "finish_reason": "stop"}],
                      "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}}
        body = json.dumps(result).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


@pytest.mark.parametrize("adapter", ["chat", "embedding", "rerank"])
def test_provider_connections_ignore_implicit_broken_proxy(monkeypatch, adapter):
    for key in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"):
        monkeypatch.setenv(key, "http://127.0.0.1:9")
    monkeypatch.setenv("NO_PROXY", "")
    monkeypatch.setenv("no_proxy", "")
    server = ThreadingHTTPServer(("127.0.0.1", 0), Provider)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base_url = f"http://127.0.0.1:{server.server_port}/v1"
    settings = Settings(llm_router_enabled=True, llm_api_key="test-key", llm_model="test-model",
                        llm_base_url=base_url, llm_timeout_seconds=1,
                        embedding_provider="openai_compatible", embedding_api_key="test-key",
                        embedding_model="test-model", embedding_base_url=base_url, embedding_timeout_seconds=1,
                        rag_rerank_enabled=True, rag_rerank_base_url=base_url, rag_rerank_timeout_seconds=1)
    try:
        if adapter == "chat":
            assert build_chat_model(settings).invoke("hello").content == "ok"
        elif adapter == "embedding":
            assert build_embedding_model(settings).embed_query("hello") == [1.0, 0.5]
        else:
            hit = SearchHit(id="test", index="test", title="test", content="test", source="test", score=1)
            result = RagReranker(settings, strict=True).rerank("hello", [hit], size=1)
            assert result.applied and result.hits[0].id == "test"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
