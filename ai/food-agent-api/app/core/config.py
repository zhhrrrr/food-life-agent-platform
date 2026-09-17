import os
from functools import lru_cache
from pathlib import Path

from pydantic import BaseModel


class Settings(BaseModel):
    app_name: str = "Food Life Agent API"
    app_version: str = "0.3.0"
    app_port: int = 8500
    log_level: str = "INFO"
    java_gateway_base_url: str = "http://127.0.0.1:8080"
    es_enabled: bool = False
    es_url: str = "http://127.0.0.1:9200"
    es_index_shop: str = "food_shop_index"
    es_index_package: str = "food_package_index"
    es_index_rule: str = "food_rule_index"


def _read_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1) # 第二个参数 1 是 最大分割次数 maxsplit 
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


def _get_value(dotenv_values: dict[str, str], key: str, default: str) -> str:
    return os.getenv(key, dotenv_values.get(key, default)) # 第二个参数就是 默认值

# 函数结果缓存装饰器 最多只缓存 1 组调用结果
@lru_cache(maxsize=1)
def get_settings() -> Settings:
    dotenv_values = _read_dotenv(Path.cwd() / ".env")
    defaults = Settings()

    return Settings(
        app_name=_get_value(dotenv_values, "APP_NAME", defaults.app_name),
        app_version=_get_value(dotenv_values, "APP_VERSION", defaults.app_version),
        app_port=int(_get_value(dotenv_values, "APP_PORT", str(defaults.app_port))),
        log_level=_get_value(dotenv_values, "LOG_LEVEL", defaults.log_level),
        java_gateway_base_url=_get_value(
            dotenv_values,
            "JAVA_GATEWAY_BASE_URL",
            defaults.java_gateway_base_url,
        ),
        es_enabled=_get_value(
            dotenv_values,
            "ES_ENABLED",
            str(defaults.es_enabled),
        ).lower()
        in {"1", "true", "yes", "on"},
        es_url=_get_value(dotenv_values, "ES_URL", defaults.es_url),
        es_index_shop=_get_value(dotenv_values, "ES_INDEX_SHOP", defaults.es_index_shop),
        es_index_package=_get_value(
            dotenv_values,
            "ES_INDEX_PACKAGE",
            defaults.es_index_package,
        ),
        es_index_rule=_get_value(dotenv_values, "ES_INDEX_RULE", defaults.es_index_rule),
    )
