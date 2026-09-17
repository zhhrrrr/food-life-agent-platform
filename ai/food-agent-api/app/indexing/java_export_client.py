from typing import Any

import httpx

from app.core.config import Settings, get_settings
from app.search.index_names import SEARCH_SCOPES
from app.search.schemas import SearchDocument


class JavaSearchExportError(Exception):
    pass


class JavaSearchExportClient:
    _ENDPOINTS = {
        "shops": ("business", "/api/internal/search/shops"),
        "packages": ("business", "/api/internal/search/packages"),
        "reviews": ("business", "/api/internal/search/reviews"),
        "activities": ("trade", "/api/trade/internal/search/activities"),
        "coupons": ("trade", "/api/trade/internal/search/coupons"),
    }
    _DOCUMENT_ENDPOINTS = {
        "shops": ("business", "/api/internal/search/shops/{entity_id}"),
        "packages": ("business", "/api/internal/search/packages/{entity_id}"),
        "reviews": ("business", "/api/internal/search/reviews/{entity_id}"),
        "coupons": ("trade", "/api/trade/internal/search/coupons/{entity_id}"),
    }

    def __init__(
        self,
        settings: Settings | None = None,
        timeout_seconds: float = 10.0,
    ) -> None:
        self.settings = settings or get_settings()
        self.timeout_seconds = timeout_seconds

    def fetch_documents(
        self,
        scope: str,
        limit: int | None = None,
    ) -> list[SearchDocument]:
        if scope not in SEARCH_SCOPES:
            raise JavaSearchExportError(f"unsupported search scope: {scope}")

        service_name, path = self._ENDPOINTS[scope]
        base_url = self._base_url(service_name)
        params = {"limit": limit} if limit is not None else None

        try:
            response = httpx.get(
                self._join_url(base_url, path),
                params=params,
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            payload = response.json()
        except Exception as exc:
            raise JavaSearchExportError(f"failed to fetch {scope} documents") from exc

        return self._parse_payload(scope=scope, payload=payload)

    def fetch_document(
        self,
        scope: str,
        entity_id: int | str,
        entity_type: str | None = None,
    ) -> SearchDocument | None:
        service_name, path = self._document_endpoint(scope=scope, entity_type=entity_type)
        base_url = self._base_url(service_name)
        path = path.format(entity_id=entity_id)

        try:
            response = httpx.get(
                self._join_url(base_url, path),
                timeout=self.timeout_seconds,
            )
            response.raise_for_status()
            payload = response.json()
        except Exception as exc:
            raise JavaSearchExportError(f"failed to fetch {scope} document {entity_id}") from exc

        return self._parse_document_payload(scope=scope, payload=payload)

    def _document_endpoint(self, scope: str, entity_type: str | None) -> tuple[str, str]:
        if scope == "activities":
            normalized_type = (entity_type or "").upper()
            if normalized_type == "GROUP_BUY":
                return "trade", "/api/trade/internal/search/activities/group-buy/{entity_id}"
            if normalized_type == "SECKILL":
                return "trade", "/api/trade/internal/search/activities/seckill/{entity_id}"
            raise JavaSearchExportError("activity entity_type must be GROUP_BUY or SECKILL")
        if scope not in self._DOCUMENT_ENDPOINTS:
            raise JavaSearchExportError(f"unsupported document scope: {scope}")
        return self._DOCUMENT_ENDPOINTS[scope]

    def _base_url(self, service_name: str) -> str:
        if service_name == "business":
            return self.settings.java_business_base_url
        if service_name == "trade":
            return self.settings.java_trade_base_url
        raise JavaSearchExportError(f"unsupported service: {service_name}")

    @staticmethod
    def _join_url(base_url: str, path: str) -> str:
        return f"{base_url.rstrip('/')}/{path.lstrip('/')}"

    @staticmethod
    def _parse_payload(scope: str, payload: dict[str, Any]) -> list[SearchDocument]:
        code = str(payload.get("code", ""))
        if code != "0000":
            message = payload.get("message") or "unknown java export error"
            raise JavaSearchExportError(f"{scope} export failed: {code} {message}")

        data = payload.get("data") or {}
        documents = data.get("documents") or []
        if not isinstance(documents, list):
            raise JavaSearchExportError(f"{scope} export returned invalid documents")

        return [SearchDocument(**document) for document in documents]

    @staticmethod
    def _parse_document_payload(scope: str, payload: dict[str, Any]) -> SearchDocument | None:
        code = str(payload.get("code", ""))
        if code != "0000":
            message = payload.get("message") or "unknown java export error"
            raise JavaSearchExportError(f"{scope} document export failed: {code} {message}")

        data = payload.get("data")
        if data is None:
            return None
        if not isinstance(data, dict):
            raise JavaSearchExportError(f"{scope} document export returned invalid data")
        return SearchDocument(**data)
