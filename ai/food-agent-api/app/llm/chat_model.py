from typing import Any

from app.core.config import Settings, get_settings


class ChatModelUnavailableError(RuntimeError):
    pass


def build_chat_model(settings: Settings | None = None) -> Any:
    resolved_settings = settings or get_settings()
    if not resolved_settings.llm_router_enabled:
        raise ChatModelUnavailableError("LLM router is disabled")
    if resolved_settings.llm_provider != "openai":
        raise ChatModelUnavailableError(
            f"unsupported LLM provider: {resolved_settings.llm_provider}"
        )
    if not resolved_settings.llm_api_key:
        raise ChatModelUnavailableError("LLM API key is not configured")

    try:
        from langchain_openai import ChatOpenAI
    except ImportError as exc:
        raise ChatModelUnavailableError("langchain-openai is not installed") from exc

    return ChatOpenAI(
        model=resolved_settings.llm_model,
        api_key=resolved_settings.llm_api_key,
        base_url=resolved_settings.llm_base_url or None,
        temperature=resolved_settings.llm_temperature,
        timeout=resolved_settings.llm_timeout_seconds,
    )
