from app.core.config import Settings


def collection_alias_for_scope(settings: Settings, scope: str) -> str:
    suffixes = {
        "shops": "shop_vectors",
        "packages": "package_vectors",
        "reviews": "review_vectors",
        "activities": "activity_vectors",
        "coupons": "coupon_vectors",
        "rules": "rule_vectors",
    }
    return f"{settings.milvus_collection_prefix}_{suffixes[scope]}"


def collection_aliases_for_scopes(
    settings: Settings,
    scopes: list[str],
) -> list[str]:
    return [
        collection_alias_for_scope(settings, scope)
        for scope in dict.fromkeys(scopes)
    ]
