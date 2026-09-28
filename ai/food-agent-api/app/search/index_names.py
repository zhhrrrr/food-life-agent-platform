from app.core.config import Settings

MYSQL_SEARCH_SCOPES = ("shops", "packages", "reviews", "activities", "coupons")
DOCUMENT_SEARCH_SCOPES = ("rules",)
SEARCH_SCOPES = MYSQL_SEARCH_SCOPES + DOCUMENT_SEARCH_SCOPES
RECOMMEND_SEARCH_SCOPES = MYSQL_SEARCH_SCOPES


def recommend_scopes_for_capabilities(
    capabilities: list[str] | None,
) -> list[str]:
    if not capabilities:
        return list(RECOMMEND_SEARCH_SCOPES)

    scopes_by_capability = {
        "shop_recommendation": ("shops", "packages", "reviews"),
        "package_recommendation": ("packages", "shops", "activities", "coupons"),
        "cuisine_filter": ("shops", "packages", "reviews"),
        "budget_filter": ("packages", "shops", "activities", "coupons"),
        "scene_recommendation": ("shops", "packages", "reviews"),
        "review_semantic_search": ("reviews", "shops"),
        "shop_comparison": ("shops", "packages", "reviews"),
    }
    scopes = [
        scope
        for capability in capabilities
        for scope in scopes_by_capability.get(capability, ())
    ]
    if not scopes:
        return list(RECOMMEND_SEARCH_SCOPES)
    return list(dict.fromkeys(scopes))


def index_names_for_scopes(settings: Settings, scopes: list[str]) -> list[str]:
    return [index_name_for_scope(settings, scope) for scope in scopes]


def index_name_for_scope(settings: Settings, scope: str) -> str:
    names = {
        "shops": settings.es_index_shop,
        "packages": settings.es_index_package,
        "reviews": settings.es_index_review,
        "activities": settings.es_index_activity,
        "coupons": settings.es_index_coupon,
        "rules": settings.es_index_rule,
    }
    return names[scope]
