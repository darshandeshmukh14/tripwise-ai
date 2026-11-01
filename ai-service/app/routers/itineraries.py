from typing import Annotated

from fastapi import APIRouter, Depends

from app.config import Settings, get_settings
from app.graph.builder import deadline_after, run_plan
from app.llm.base import LlmProvider
from app.llm.factory import get_llm
from app.refine import refine
from app.schemas import ItineraryResponse, RefineRequest, RefineResponse, TripRequest

router = APIRouter(prefix="/internal/v1/itineraries", tags=["itineraries"])


@router.post("/generate", response_model=ItineraryResponse, response_model_by_alias=True)
async def generate(
    request: TripRequest,
    llm: Annotated[LlmProvider, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ItineraryResponse:
    return await run_plan(request, llm, max_loops=settings.budget_guard_max_loops,
                          deadline=deadline_after(settings.plan_deadline_seconds),
                          city_timeout=settings.city_lookup_timeout_seconds)


@router.post("/refine", response_model=RefineResponse, response_model_by_alias=True)
async def refine_itinerary(
    request: RefineRequest,
    llm: Annotated[LlmProvider, Depends(get_llm)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> RefineResponse:
    return await refine(request, llm, settings.budget_guard_max_loops,
                        deadline=deadline_after(settings.plan_deadline_seconds),
                        city_timeout=settings.city_lookup_timeout_seconds)
