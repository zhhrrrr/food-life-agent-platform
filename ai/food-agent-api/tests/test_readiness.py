from concurrent.futures import Future

from app.core.config import Settings
from app.core.readiness import _probe_result, readiness_checks


def test_readiness_can_skip_runtime_probes_for_hermetic_tests() -> None:
    checks = readiness_checks(
        Settings(
            readiness_runtime_checks_enabled=False,
            gateway_trusted_secret="secret",
        )
    )

    assert checks["gateway_identity"] is True
    assert "java_gateway" not in checks


def test_failed_runtime_probe_is_reported_not_raised(monkeypatch) -> None:
    monkeypatch.setattr("app.core.readiness._gateway_ready", lambda _settings: False)

    checks = readiness_checks(
        Settings(
            readiness_runtime_checks_enabled=True,
            gateway_trusted_secret="secret",
        )
    )

    assert checks["java_gateway"] is False


def test_probe_exception_becomes_false() -> None:
    future: Future[bool] = Future()
    future.set_exception(RuntimeError("unavailable"))

    assert _probe_result(future) is False
