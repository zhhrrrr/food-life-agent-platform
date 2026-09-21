from typing import Any

from app.core.config import Settings, get_settings

ZHIPU_OPENAI_COMPATIBLE_BASE_URL = "https://api.z.ai/api/paas/v4/"


class ChatModelUnavailableError(RuntimeError):
    pass


def resolve_chat_model_options(settings: Settings) -> dict[str, Any]:
    provider = settings.llm_provider.strip().lower()
    if provider not in {"openai", "zhipu"}:
        raise ChatModelUnavailableError(f"unsupported LLM provider: {settings.llm_provider}")
    if not settings.llm_api_key:
        raise ChatModelUnavailableError("LLM API key is not configured")

    base_url = settings.llm_base_url.strip() or None
    if provider == "zhipu" and base_url is None:
        base_url = ZHIPU_OPENAI_COMPATIBLE_BASE_URL

    return {
        "model": settings.llm_model,
        "api_key": settings.llm_api_key,
        "base_url": base_url,
        "temperature": settings.llm_temperature,
        "timeout": settings.llm_timeout_seconds,
    }


def build_chat_model(settings: Settings | None = None) -> Any:
    resolved_settings = settings or get_settings()
    if not resolved_settings.llm_router_enabled:
        raise ChatModelUnavailableError("LLM router is disabled")
    options = resolve_chat_model_options(resolved_settings)

    try:
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise ChatModelUnavailableError("langchain-openai is not installed") from exc

    return ChatOpenAI(**options)
