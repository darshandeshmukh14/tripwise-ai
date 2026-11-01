"""Deterministic day plan used by the fake provider and as the fallback when the LLM is slow or fails."""

from app.llm.base import DayPlanContext
from app.llm.models import DayPlanDraft, LlmActivity, LlmDay
from app.schemas import TimeOfDay

_DEFAULT_INTERESTS = ["local sights", "food", "culture", "nature"]


def _city_for_day(ctx: DayPlanContext, day: int) -> str:
    for city, first, last in ctx.stops:
        if first <= day <= last:
            return city
    return ctx.destination


def template_days(ctx: DayPlanContext) -> DayPlanDraft:
    interests = ctx.interests or _DEFAULT_INTERESTS
    days = []
    previous = None
    for i in range(1, ctx.days + 1):
        focus = interests[(i - 1) % len(interests)]
        city = _city_for_day(ctx, i)
        if i == 1:
            title = "Arrival and settling in"
        elif previous is not None and city != previous:
            title = f"Travel to {city}"
        else:
            title = f"{focus.title()} day"
        previous = city
        days.append(
            LlmDay(
                day=i,
                title=title,
                summary=f"A {ctx.pace} day in {city} focused on {focus}.",
                activities=[
                    LlmActivity(time_of_day=TimeOfDay.MORNING, title=f"Explore {focus}",
                                description=f"Start the day with {focus} in {city}."),
                    LlmActivity(time_of_day=TimeOfDay.AFTERNOON, title="Local lunch",
                                description="Try a well-reviewed local restaurant."),
                    LlmActivity(time_of_day=TimeOfDay.EVENING, title="Evening stroll",
                                description="Relax at a popular evening spot."),
                ],
            )
        )
    return DayPlanDraft(trip_title=f"{ctx.days}-day {ctx.destination} trip",
                        area_suggestion=f"Central {ctx.destination}" if not ctx.stops else "", days=days)
