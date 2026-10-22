from __future__ import annotations

from app.tools.models import ToolSpec


class ToolRegistry:
    def __init__(self, specs: list[ToolSpec] | None = None) -> None:
        self._specs: dict[str, ToolSpec] = {}
        for spec in specs or []:
            self.register(spec)

    def register(self, spec: ToolSpec) -> None:
        if spec.name in self._specs:
            raise ValueError(f"duplicate tool: {spec.name}")
        self._specs[spec.name] = spec

    def get(self, name: str) -> ToolSpec | None:
        return self._specs.get(name)

    def select(
        self, *, capabilities: set[str] | None = None,
        names: set[str] | frozenset[str] | None = None, risk: str | None = None,
    ) -> list[ToolSpec]:
        """Intersect explicit tool names, business capabilities and risk."""
        return [spec for spec in self._specs.values()
                if (names is None or spec.name in names)
                and (capabilities is None or spec.capabilities.intersection(capabilities))
                and (risk is None or spec.risk == risk)]

    def llm_schemas(self, **filters) -> list[dict[str, object]]:
        return [spec.llm_schema() for spec in self.select(**filters)]

    def supports(self, name: str, capabilities: set[str]) -> bool:
        spec = self.get(name)
        return bool(spec and spec.capabilities.intersection(capabilities))
