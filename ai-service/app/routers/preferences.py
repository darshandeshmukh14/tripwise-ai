from typing import Annotated

from fastapi import APIRouter, Depends

from app.clarify import clarify
from app.config import Settings, get_settings
from app.llm.base import LlmProvider
from app.llm.factory import get_llm
from app.schemas import ClarifyResponse, TripRequest

router = APIRouter(prefix="/internal/v1/preferences", tags=["preferences"])


@router.post("/clarify", response_model=ClarifyResponse, response_model_by_alias=True)
async def clarify_preferences(
    request: TripRequest,
    llm: Annotated[LlmProvider, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ClarifyResponse:
    return await clarify(request, llm, settings.city_lookup_timeout_seconds)
