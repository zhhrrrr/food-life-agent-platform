from app.core.config import Settings
from app.indexing.java_export_client import JavaSearchExportClient


def test_java_export_uses_internal_service_identity(monkeypatch) -> None:
    captured: dict[str, object] = {}

    class Response:
        def raise_for_status(self) -> None:
            return None

        def json(self) -> dict[str, object]:
            return {"code": "0000", "data": {"documents": []}}

    def fake_get(url: str, **kwargs):
        captured["url"] = url
        captured.update(kwargs)
        return Response()

    monkeypatch.setattr("app.indexing.java_export_client.httpx.get", fake_get)
    client = JavaSearchExportClient(
        Settings(
            gateway_trusted_secret_header="x-internal-secret",
            gateway_trusted_secret="service-secret",
        )
    )

    assert client.fetch_documents("shops") == []
    assert captured["headers"] == {
        "x-internal-call": "food-life-agent",
        "x-internal-secret": "service-secret",
    }
