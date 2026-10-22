"""Shared provider connection pools; system proxies require explicit opt-in."""
from functools import lru_cache

import httpx


@lru_cache(maxsize=2)
def provider_http_client(trust_env: bool) -> httpx.Client:
    return httpx.Client(trust_env=trust_env)


@lru_cache(maxsize=2)
def provider_async_client(trust_env: bool) -> httpx.AsyncClient:
    return httpx.AsyncClient(trust_env=trust_env)
