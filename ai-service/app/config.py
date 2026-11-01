from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Environment-driven configuration. Secrets have no defaults: the service refuses to start without them."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    internal_api_token: SecretStr = Field(min_length=16)
    # gemini = Google Gemini API; openrouter = any model on https://openrouter.ai; fake = deterministic offline
    llm_provider: Literal["gemini", "openrouter", "fake"] = "gemini"
    gemini_api_key: SecretStr | None = None
    # Model names change often; override via env after checking the current Gemini model list.
    gemini_model: str = "gemini-2.5-flash"
    openrouter_api_key: SecretStr | None = None
    # Any OpenRouter model id ("vendor/model"); pick one that supports structured outputs (response_format).
    openrouter_model: str = "openai/gpt-4o-mini"
    openrouter_base_url: str = "https://openrouter.ai/api/v1"
    # Optional OpenRouter attribution headers (HTTP-Referer / X-Title)
    openrouter_site_url: str = ""
    openrouter_app_name: str = "TripWise AI"
    llm_timeout_seconds: float = Field(default=40.0, gt=0, le=55)
    # Whole generate/refine request; keep below Spring's 60 s read timeout.
    plan_deadline_seconds: float = Field(default=50.0, gt=0, le=58)
    city_lookup_timeout_seconds: float = Field(default=10.0, gt=0, le=30)
    llm_temperature: float = Field(default=0.6, ge=0, le=1.5)
    budget_guard_max_loops: int = Field(default=2, ge=0, le=5)
    log_level: str = "INFO"

    # FX: open_er_api = free daily rates (https://open.er-api.com, attribution required); static = bundled table
    fx_provider: Literal["open_er_api", "static"] = "open_er_api"
    fx_timeout_seconds: float = Field(default=3.0, gt=0, le=10)
    fx_cache_ttl_seconds: int = Field(default=86400, ge=60)

    # Web price hints via Gemini Google Search grounding (billed separately; off by default)
    grounding_enabled: bool = False
    grounding_daily_cap: int = Field(default=200, ge=0)
    web_hints_per_plan: int = Field(default=4, ge=0, le=10)
    price_hint_timeout_seconds: float = Field(default=12.0, gt=0, le=30)
    price_hint_cache_ttl_seconds: int = Field(default=86400, ge=60)
    # Global daily cap on all Gemini calls (0 = unlimited); over the cap the deterministic fallbacks are used
    llm_daily_cap: int = Field(default=0, ge=0)

    # Booking links: JSON object {provider: url-template} overriding the defaults in app/prices/links.py
    link_templates: str = ""
    affiliate_skyscanner_id: str = ""
    affiliate_booking_aid: str = ""
    affiliate_airbnb_id: str = ""
    affiliate_redbus_id: str = ""
    affiliate_12go_id: str = ""

    @model_validator(mode="after")
    def _require_provider_key(self) -> "Settings":
        if self.llm_provider == "gemini" and not (
            self.gemini_api_key and self.gemini_api_key.get_secret_value()
        ):
            raise ValueError("GEMINI_API_KEY is required when LLM_PROVIDER=gemini")
        if self.llm_provider == "openrouter":
            if not (self.openrouter_api_key and self.openrouter_api_key.get_secret_value()):
                raise ValueError("OPENROUTER_API_KEY is required when LLM_PROVIDER=openrouter")
            if not self.openrouter_base_url.startswith("https://"):
                raise ValueError("OPENROUTER_BASE_URL must use https")
        return self

    @property
    def web_hints_available(self) -> bool:
        """Web price hints need Gemini Google Search grounding; other real providers skip them."""
        return self.grounding_enabled and self.llm_provider != "openrouter"


@lru_cache
def get_settings() -> Settings:
    return Settings()
