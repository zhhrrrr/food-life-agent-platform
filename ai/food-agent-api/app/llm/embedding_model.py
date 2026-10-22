from functools import lru_cache
from typing import Any

from app.core.config import Settings, get_settings
from app.llm.chat_model import ZHIPU_OPENAI_COMPATIBLE_BASE_URL
from app.llm.transport import provider_async_client, provider_http_client

SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL = "https://api.siliconflow.cn/v1"


class EmbeddingModelUnavailableError(RuntimeError):
    pass


def resolve_embedding_model_options(settings: Settings) -> dict[str, Any]:
    provider = settings.embedding_provider.strip().lower()
    if provider not in {"openai", "zhipu", "siliconflow", "openai_compatible"}:
        raise EmbeddingModelUnavailableError(
            f"unsupported embedding provider: {settings.embedding_provider}"
        )
    if not settings.embedding_api_key.strip():
        raise EmbeddingModelUnavailableError("embedding API key is not configured")
    if not settings.embedding_model.strip():
        raise EmbeddingModelUnavailableError("embedding model is not configured")

    base_url = settings.embedding_base_url.strip() or None
    if provider == "zhipu" and base_url is None:
        base_url = ZHIPU_OPENAI_COMPATIBLE_BASE_URL
    if provider == "siliconflow" and base_url is None:
        base_url = SILICONFLOW_OPENAI_COMPATIBLE_BASE_URL
    if provider == "openai_compatible" and base_url is None:
        raise EmbeddingModelUnavailableError(
            "embedding base URL is required for openai_compatible provider"
        )

    return {
        "model": settings.embedding_model,
        "api_key": settings.embedding_api_key,
        "base_url": base_url,
        "request_timeout": settings.embedding_timeout_seconds,
        "max_retries": 2,
        "check_embedding_ctx_length": False,
    }


def build_embedding_model(settings: Settings | None = None) -> Any:
    resolved_settings = settings or get_settings()
    options = resolve_embedding_model_options(resolved_settings)
    return _cached_embedding_model(
        str(options["model"]),
        str(options["api_key"]),
        str(options["base_url"] or ""),
        float(options["request_timeout"]),
        resolved_settings.model_trust_env,
    )


@lru_cache(maxsize=4)
def _cached_embedding_model(
    model: str,
    api_key: str,
    base_url: str,
    request_timeout: float,
    trust_env: bool,
) -> Any:
    try:
        from langchain_openai import OpenAIEmbeddings
    except ImportError as exc:
        raise EmbeddingModelUnavailableError(
            "langchain-openai is not installed"
        ) from exc

    return OpenAIEmbeddings(
        model=model,
        api_key=api_key,
        base_url=base_url or None,
        request_timeout=request_timeout,
        max_retries=2,
        check_embedding_ctx_length=False,
        http_client=provider_http_client(trust_env),
        http_async_client=provider_async_client(trust_env),
    )
