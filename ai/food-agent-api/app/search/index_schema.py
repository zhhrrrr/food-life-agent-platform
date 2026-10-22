TEXT_FIELD = {
    "type": "text",
    "analyzer": "cjk",
    "search_analyzer": "cjk",
}
TEXT_WITH_KEYWORD_FIELD = {
    **TEXT_FIELD,
    "fields": {"keyword": {"type": "keyword", "ignore_above": 256}},
}


def index_definition(
    scope: str,
    shards: int,
    replicas: int,
) -> dict[str, object]:
    properties: dict[str, object] = {
        "id": {"type": "keyword"},
        "title": TEXT_WITH_KEYWORD_FIELD,
        "content": TEXT_FIELD,
        "source": {"type": "keyword"},
        "category": TEXT_WITH_KEYWORD_FIELD,
        "metadata": {
            "type": "object",
            "dynamic": False,
            "properties": _metadata_properties(scope),
        },
    }
    return {
        "settings": {
            "number_of_shards": shards,
            "number_of_replicas": replicas,
        },
        "mappings": {
            "dynamic": "strict",
            "properties": properties,
        },
    }


def _metadata_properties(scope: str) -> dict[str, object]:
    common_names = {
        "shopName": TEXT_WITH_KEYWORD_FIELD,
        "packageName": TEXT_WITH_KEYWORD_FIELD,
        "couponName": TEXT_WITH_KEYWORD_FIELD,
    }
    properties_by_scope: dict[str, dict[str, object]] = {
        "shops": {
            "shopId": {"type": "long"},
            "categoryId": {"type": "long"},
            "categoryName": TEXT_WITH_KEYWORD_FIELD,
            "area": {"type": "keyword"},
            "address": TEXT_FIELD,
            "avgPrice": {"type": "long"},
            "comments": {"type": "long"},
            "score": {"type": "double"},
            "openHours": {"type": "keyword"},
        },
        "packages": {
            "packageId": {"type": "long"},
            "shopId": {"type": "long"},
            "price": {"type": "long"},
            "originalPrice": {"type": "long"},
            "status": {"type": "integer"},
            "useRule": TEXT_FIELD,
        },
        "reviews": {
            "reviewId": {"type": "long"},
            "reviewNo": {"type": "keyword"},
            "shopId": {"type": "long"},
            "packageId": {"type": "long"},
            "score": {"type": "double"},
            "images": {"type": "keyword", "ignore_above": 2048},
            "createTime": {"type": "date"},
        },
        "activities": {
            "activityId": {"type": "long"},
            "activityType": {"type": "keyword"},
            "packageId": {"type": "long"},
            "targetCount": {"type": "integer"},
            "userTakeLimit": {"type": "integer"},
            "activityPrice": {"type": "long"},
            "validStartTime": {"type": "date"},
            "validEndTime": {"type": "date"},
        },
        "coupons": {
            "couponId": {"type": "long"},
            "couponType": {"type": "keyword"},
            "thresholdAmount": {"type": "long"},
            "discountAmount": {"type": "long"},
            "scopeType": {"type": "keyword"},
            "scopeShopId": {"type": "long"},
            "scopePackageId": {"type": "long"},
            "userReceiveLimit": {"type": "integer"},
            "validStartTime": {"type": "date"},
            "validEndTime": {"type": "date"},
        },
        "rules": {
            "doc_type": {"type": "keyword"},
            "domain": {"type": "keyword"},
            "tags": {"type": "keyword"},
            "source_type": {"type": "keyword"},
            "language": {"type": "keyword"},
            "version": {"type": "keyword"},
            "effectiveStartTime": {"type": "date"},
            "effectiveEndTime": {"type": "date"},
        },
    }
    return {**common_names, **properties_by_scope[scope]}
