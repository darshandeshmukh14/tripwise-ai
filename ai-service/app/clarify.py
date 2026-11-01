"""Rule-based clarifying questions: only for inputs that are missing or conflict with the price estimate."""

from app.graph import nodes
from app.graph.state import PlanState
from app.llm.base import LlmProvider
from app.schemas import ClarifyQuestion, ClarifyResponse, TripRequest

INTEREST_OPTIONS = [
    "Beaches", "Mountains", "Heritage & history", "Food", "Nightlife", "Adventure sports",
    "Wildlife", "Shopping", "Spiritual", "Art & culture",
]


async def estimate(request: TripRequest, llm: LlmProvider, city_timeout: float | None = None) -> PlanState:
    """Runs the deterministic pricing nodes once (no web hints, budget guard or itinerary)."""
    state: PlanState = {"request": request, "llm": llm, "city_timeout": city_timeout}
    state.update(await nodes.intake(state))
    for step in (nodes.transport, nodes.stay, nodes.costs):
        state.update(step(state))
    return state


async def clarify(request: TripRequest, llm: LlmProvider, city_timeout: float | None = None) -> ClarifyResponse:
    answered = {k for k, v in request.answers.items() if v and v.strip()}
    state = await estimate(request, llm, city_timeout)
    questions: list[ClarifyQuestion] = []

    def ask(question: ClarifyQuestion) -> None:
        if question.id not in answered:
            questions.append(question)

    total = state["total"]
    cur = request.currency
    if request.budget_amount is None:
        ask(ClarifyQuestion(
            id="budget", type="number",
            text=f"What is your total budget in {cur}? A {request.comfort_level.value.lower()} trip like this "
                 f"typically costs {cur} {total.min:.0f} to {total.max:.0f}.",
        ))
    elif total.min > request.budget_amount:
        ask(ClarifyQuestion(
            id="budgetConflict", type="single_choice", options=["downgrade", "keep"],
            text=f"Your budget of {cur} {request.budget_amount:.0f} is below our lowest estimate of {cur} "
                 f"{total.min:.0f}. Should we lower comfort and pick cheaper transport (downgrade), "
                 "or keep your choices and go over budget (keep)?",
        ))
    if not request.preferences:
        ask(ClarifyQuestion(id="interests", type="multi_choice", options=INTEREST_OPTIONS,
                            text="What are you most interested in on this trip?"))
    if (request.travel_style or "").strip().lower() not in nodes.PACE_ALIASES:
        ask(ClarifyQuestion(id="pace", type="single_choice", options=["relaxed", "balanced", "packed"],
                            text="What pace do you prefer for each day?"))
    if any(nodes.is_overnight(q.option) for leg in state.get("legs") or [] for q in leg.quotes):
        ask(ClarifyQuestion(id="overnightTravel", type="single_choice", options=["yes", "no"],
                            text="Some bus or train options are overnight journeys. Are you comfortable "
                                 "travelling overnight?"))
    return ClarifyResponse(questions=questions)
