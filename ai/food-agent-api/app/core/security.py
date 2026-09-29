import hmac
from collections.abc import Mapping

from fastapi import Header, HTTPException, status

from app.core.config import Settings, get_settings


def trusted_user_id(headers: Mapping[str, str], settings: Settings) -> str:
    expected = settings.gateway_trusted_secret
    supplied = headers.get(settings.gateway_trusted_secret_header, "")
    if not expected or not hmac.compare_digest(supplied, expected):
        return ""
    return headers.get("x-user-id", "").strip()


def require_index_admin(
    x_agent_admin_token: str = Header(default=""),
) -> None:
    expected = get_settings().agent_admin_token
    if not expected:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="index administration is disabled",
        )
    if not hmac.compare_digest(x_agent_admin_token, expected):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="invalid index administration token",
        )
