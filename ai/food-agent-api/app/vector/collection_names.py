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


def collection_aliases_for_indices(
    settings: Settings,
    indices: list[str],
) -> list[str]:
    scopes_by_index = {
        settings.es_index_shop: "shops",
        settings.es_index_package: "packages",
        settings.es_index_review: "reviews",
        settings.es_index_activity: "activities",
        settings.es_index_coupon: "coupons",
        settings.es_index_rule: "rules",
    }
    return [
        collection_alias_for_scope(settings, scopes_by_index[index])
        for index in dict.fromkeys(indices)
        if index in scopes_by_index
    ]
