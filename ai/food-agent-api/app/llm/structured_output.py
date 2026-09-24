from collections.abc import Sequence
from typing import Any, TypeVar

from pydantic import BaseModel

from app.core.config import Settings
from app.llm.chat_model import build_chat_model

StructuredModel = TypeVar("StructuredModel", bound=BaseModel)


def invoke_structured_output(
    *,
    settings: Settings,
    schema: type[StructuredModel],
    messages: Sequence[tuple[str, str]],
    chat_model: Any | None = None,
) -> StructuredModel:
    model = chat_model or build_chat_model(settings)
    structured_model = model.with_structured_output(
        schema,
        **structured_output_kwargs(settings),
    )
    result = structured_model.invoke(list(messages))
    return result if isinstance(result, schema) else schema.model_validate(result)


def structured_output_kwargs(settings: Settings) -> dict[str, str]:
    if settings.llm_provider.strip().lower() == "zhipu":
        return {"method": "json_mode"}
    return {}
