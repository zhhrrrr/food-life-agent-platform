from app.core.config import Settings


def recommend_indices(settings: Settings) -> list[str]:
    return [settings.es_index_shop, settings.es_index_package]


def support_indices(settings: Settings) -> list[str]:
    return [settings.es_index_rule]
