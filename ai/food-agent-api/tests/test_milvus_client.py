import pytest

from app.core.config import Settings
from app.core.readiness import _milvus_ready
from app.vector.milvus_client import _connect, get_milvus_client


@pytest.mark.parametrize("consumer", ["vector", "readiness"])
def test_milvus_clients_avoid_server_rejection_of_idle_pings(monkeypatch, consumer) -> None:
    captured = {}

    class Client:
        def __init__(self, **options):
            captured.update(options)

        def list_collections(self, **kwargs):
            return []

        def close(self):
            pass

    monkeypatch.setattr("pymilvus.MilvusClient", Client)
    settings = Settings(
        milvus_enabled=True,
        milvus_uri="http://milvus.example:19530",
        milvus_database="food",
        milvus_token="test-token",
        milvus_timeout_seconds=7,
        readiness_timeout_seconds=2,
    )
    _connect.cache_clear()
    try:
        if consumer == "vector":
            assert get_milvus_client(settings) is not None
            assert captured["timeout"] == 7
        else:
            assert _milvus_ready(settings) is True
            assert captured["timeout"] == 2
    finally:
        _connect.cache_clear()

    assert captured["uri"] == "http://milvus.example:19530"
    assert captured["db_name"] == "food"
    assert captured["token"] == "test-token"
    grpc_options = captured.get("grpc_options", {})
    assert grpc_options.get("grpc.keepalive_permit_without_calls") is False
    assert grpc_options.get("grpc.keepalive_time_ms", 10000) >= 300000
