"""Chat refinement: the LLM decides the action; prices are always recomputed by the pricing graph."""

import asyncio
import json
import logging
from decimal import Decimal

from pydantic import ValidationError

from app.graph import nodes
from app.graph.builder import run_plan
from app.llm.base import LlmError, LlmProvider, RefineContext
from app.llm.models import LlmActivity, LlmDay, RefineAction, RefineChanges
from app.schemas import ItineraryResponse, RefineRequest, RefineResponse, TripRequest

log = logging.getLogger(__name__)
MAX_DAYS_JSON_CHARS = 12000


def parse_plan(raw: dict | None) -> ItineraryResponse | None:
    if not raw:
        return None
    try:
        return ItineraryResponse.model_validate(raw)
    except ValidationError:
        log.info("Stored plan does not match the current schema; falling back to a full replan")
        return None


def to_llm_days(plan: ItineraryResponse) -> list[LlmDay]:
    return [
        LlmDay(day=d.day, title=d.title, summary=d.summary,
               activities=[LlmActivity(time_of_day=a.time_of_day, title=a.title, description=a.description)
                           for a in d.activities])
        for d in plan.days
    ]


def _trip_summary(trip: TripRequest) -> str:
    places = " -> ".join(f"{s.city} ({s.nights}n)" for s in trip.stops) if trip.multi_city else trip.destination
    return (
        f"{trip.origin or 'origin not set'} to {places}, {trip.start_date} to {trip.end_date} "
        f"({trip.days} days), {trip.travellers_count} traveller(s), comfort {trip.comfort_level.value}, "
        f"transport {', '.join(m.value for m in trip.transport_modes) or 'any'}, "
        f"budget {trip.currency + ' ' + str(trip.budget_amount) if trip.budget_amount else 'not set'}, "
        f"style {trip.travel_style or 'not set'}, interests {', '.join(trip.preferences) or 'not set'}"
    )


def _plan_summary(plan: ItineraryResponse | None) -> str:
    if plan is None:
        return "(no valid plan yet)"
    chosen = next((o for o in plan.transport_options if o.recommended), None)
    total = plan.total_estimated_cost if plan.total_estimated_cost is not None else plan.total_estimated_cost_inr
    currency = plan.currency if plan.total_estimated_cost is not None else "INR"
    return (
        f"'{plan.trip_title}', comfort {plan.comfort_level.value}, total about {currency} {total} "
        f"({plan.budget_status.value}), arrival by {chosen.mode.value if chosen else 'n/a'}, "
        f"{plan.season.type.value} season"
    )


def apply_changes(trip: TripRequest, changes: RefineChanges | None, plan: ItineraryResponse | None) -> TripRequest:
    data = trip.model_dump()
    answers = dict(trip.answers)
    # Start from the plan's comfort (the budget guard may have lowered it)
    data["comfort_level"] = plan.comfort_level if plan else trip.comfort_level
    if changes:
        if changes.comfort_level:
            data["comfort_level"] = changes.comfort_level
            answers["budgetConflict"] = "keep"  # an explicit choice must not be undone by the budget guard
        if changes.transport_modes:
            data["transport_modes"] = list(dict.fromkeys(changes.transport_modes))[:4]
        if changes.budget_amount is not None and changes.budget_amount > 0:
            data["budget_amount"] = Decimal(str(changes.budget_amount)).quantize(Decimal("0.01"))
        if changes.pace and changes.pace.strip().lower() in nodes.PACE_ALIASES:
            answers["pace"] = nodes.PACE_ALIASES[changes.pace.strip().lower()]
        if changes.interests:
            data["preferences"] = [i.strip()[:100] for i in changes.interests if i.strip()][:50]
    data["answers"] = answers
    try:
        return TripRequest.model_validate(data)
    except ValidationError:
        log.info("Ignoring invalid refine changes")
        return trip


async def refine(
    request: RefineRequest,
    llm: LlmProvider,
    max_loops: int,
    *,
    deadline: float | None = None,
    city_timeout: float | None = None,
) -> RefineResponse:
    trip = request.trip
    plan = parse_plan(request.current_plan)
    days_json = json.dumps([d.model_dump(mode="json", by_alias=True) for d in plan.days]) if plan else "[]"
    ctx = RefineContext(
        trip_summary=_trip_summary(trip),
        plan_summary=_plan_summary(plan),
        days_json=days_json[:MAX_DAYS_JSON_CHARS],
        history=[(t.role, t.content) for t in request.history],
        message=request.message,
        trip_days=trip.days,
    )
    try:
        decision = await asyncio.wait_for(llm.refine(ctx), nodes.time_left(deadline))
    except TimeoutError as exc:
        raise LlmError("Refine timed out") from exc
    message = decision.assistant_message.strip()[:4000] or "Done."

    if decision.action is RefineAction.NONE:
        return RefineResponse(assistant_message=message)

    if decision.action is RefineAction.EDIT_DAYS and plan is not None:
        days = [nodes.to_day(i, d, trip.start_date) for i, d in enumerate(decision.days or [])]
        if len(days) != trip.days:
            return RefineResponse(assistant_message=message)
        return RefineResponse(assistant_message=message, updated_plan=plan.model_copy(update={"days": days}))

    # REPLAN (or EDIT_DAYS without a usable stored plan): recompute every price
    changed_trip = apply_changes(trip, decision.changes, plan)
    regenerate_days = decision.changes is not None and bool(decision.changes.pace or decision.changes.interests)
    keep_days = decision.days if decision.action is RefineAction.EDIT_DAYS else None
    if keep_days is None and plan is not None and not regenerate_days:
        keep_days = to_llm_days(plan)
    updated = await run_plan(
        changed_trip,
        llm,
        max_loops=max_loops,
        keep_days=keep_days,
        keep_title=plan.trip_title if plan else None,
        keep_area=plan.stay.area_suggestion if plan and plan.stay else None,
        deadline=deadline,
        city_timeout=city_timeout,
    )
    return RefineResponse(assistant_message=message, updated_plan=updated)
