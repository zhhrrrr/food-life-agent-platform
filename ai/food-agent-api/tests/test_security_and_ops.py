from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.security import trusted_user_id
from app.main import app

client = TestClient(app)


def test_user_id_is_accepted_only_from_trusted_gateway() -> None:
    settings = Settings(gateway_trusted_secret="trusted-secret")

    assert trusted_user_id({"x-user-id": "7"}, settings) == ""
    assert (
        trusted_user_id(
            {
                "x-user-id": "7",
                "x-internal-secret": "wrong-secret",
            },
            settings,
        )
        == ""
    )
    assert (
        trusted_user_id(
            {
                "x-user-id": "7",
                "x-internal-secret": "trusted-secret",
            },
            settings,
        )
        == "7"
    )


def test_index_administration_requires_separate_token() -> None:
    response = client.post("/api/agent/indexes/reconcile", json={})

    assert response.status_code == 403


def test_liveness_readiness_and_metrics_endpoints() -> None:
    live = client.get("/health/live")
    ready = client.get("/health/ready")
    metrics = client.get("/metrics")

    assert live.status_code == 200
    assert live.json()["status"] == "ok"
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"
    assert metrics.status_code == 200
    assert "food_agent_http_requests_total" in metrics.text


def test_unknown_http_methods_do_not_create_unbounded_metric_labels() -> None:
    assert client.request("CUSTOM_AUDIT_1", "/missing-audit-route").status_code == 404
    metrics = client.get("/metrics").text
    assert 'method="CUSTOM_AUDIT_1"' not in metrics
    assert 'method="OTHER"' in metrics
