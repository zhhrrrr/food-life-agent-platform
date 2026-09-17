from app.core.config import Settings

SEARCH_SCOPES = ("shops", "packages", "reviews", "activities", "coupons")


def recommend_indices(settings: Settings) -> list[str]:
    return [
        settings.es_index_shop,
        settings.es_index_package,
        settings.es_index_review,
        settings.es_index_activity,
        settings.es_index_coupon,
    ]


def support_indices(settings: Settings) -> list[str]:
    return [settings.es_index_rule]


def index_name_for_scope(settings: Settings, scope: str) -> str:
    names = {
        "shops": settings.es_index_shop,
        "packages": settings.es_index_package,
        "reviews": settings.es_index_review,
        "activities": settings.es_index_activity,
        "coupons": settings.es_index_coupon,
    }
    return names[scope]


def index_name_for_source(settings: Settings, source: str) -> str:
    names = {
        settings.es_index_shop: settings.es_index_shop,
        settings.es_index_package: settings.es_index_package,
        settings.es_index_review: settings.es_index_review,
        settings.es_index_activity: settings.es_index_activity,
        settings.es_index_coupon: settings.es_index_coupon,
    }
    return names[source]
