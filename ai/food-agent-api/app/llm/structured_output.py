import json
import logging
from collections.abc import Sequence
from typing import Any, TypeVar

from pydantic import BaseModel

from app.core.config import Settings
from app.llm.chat_model import ChatModelUnavailableError, build_chat_model

StructuredModel = TypeVar("StructuredModel", bound=BaseModel)


def invoke_structured_output(
    *,
    settings: Settings,
    schema: type[StructuredModel],
    messages: Sequence[tuple[str, str]],
    chat_model: Any | None = None,
) -> StructuredModel:
    model = chat_model or build_chat_model(settings)
    kwargs = structured_output_kwargs(settings)
    supplied = list(messages)
    if kwargs.get("method") == "json_mode":
        instructions = "\nJSON Schema:\n" + json.dumps(schema.model_json_schema(), ensure_ascii=False)
        system_index = next((i for i, (role, _) in enumerate(supplied) if role == "system"), None)
        if system_index is None:
            supplied.insert(0, ("system", "Return JSON matching the supplied schema." + instructions))
        else:
            role, content = supplied[system_index]
            supplied[system_index] = (role, content + instructions)
    structured_model = model.with_structured_output(schema, **kwargs)
    result = structured_model.invoke(supplied)
    return result if isinstance(result, schema) else schema.model_validate(result)


def invoke_structured_output_with_fallback(
    *,
    settings: Settings,
    schema: type[StructuredModel],
    messages: Sequence[tuple[str, str]],
    fallback: StructuredModel,
    operation: str,
    logger: logging.Logger,
    chat_model: Any | None = None,
) -> StructuredModel:
    if not settings.llm_router_enabled:
        return fallback

    try:
        return invoke_structured_output(
            settings=settings,
            schema=schema,
            messages=messages,
            chat_model=chat_model,
        )
    except ChatModelUnavailableError as exc:
        logger.info("%s_fallback reason=%s", operation, exc)
    except Exception:
        logger.exception("%s_failed", operation)
    return fallback


def structured_output_kwargs(settings: Settings) -> dict[str, str]:
    if settings.llm_provider.strip().lower() == "zhipu":
        return {"method": "json_mode"}
    return {}
