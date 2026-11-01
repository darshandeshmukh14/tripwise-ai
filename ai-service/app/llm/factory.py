from functools import lru_cache

from app.config import get_settings
from app.llm.base import LlmProvider
from app.llm.caps import CappedLlmProvider


@lru_cache
def get_llm() -> LlmProvider:
    settings = get_settings()
    if settings.llm_provider == "fake":
        from app.llm.fake import FakeLlmProvider

        inner: LlmProvider = FakeLlmProvider()
    elif settings.llm_provider == "openrouter":
        from app.llm.openrouter import OpenRouterLlmProvider

        inner = OpenRouterLlmProvider(settings)
    else:
        from app.llm.gemini import GeminiLlmProvider

        inner = GeminiLlmProvider(settings)
    return CappedLlmProvider(inner, settings.llm_daily_cap, settings.grounding_daily_cap)
