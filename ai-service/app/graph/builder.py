import asyncio
from functools import lru_cache

from langgraph.graph import END, StateGraph
from langgraph.graph.state import CompiledStateGraph

from app.graph import nodes
from app.graph.state import PlanState
from app.llm.base import LlmProvider
from app.llm.models import LlmDay
from app.schemas import ItineraryResponse, TripRequest


@lru_cache
def get_graph() -> CompiledStateGraph:
    """intake -> transport -> stay -> hints -> costs -> budget_guard -(loop)-> itinerary -> output."""
    g = StateGraph(PlanState)
    g.add_node("intake", nodes.intake)
    g.add_node("transport", nodes.transport)
    g.add_node("stay", nodes.stay)
    g.add_node("hints", nodes.hints)
    g.add_node("costs", nodes.costs)
    g.add_node("budget_guard", nodes.budget_guard)
    g.add_node("itinerary", nodes.itinerary)
    g.add_node("output", nodes.output)
    g.set_entry_point("intake")
    g.add_edge("intake", "transport")
    g.add_edge("transport", "stay")
    g.add_edge("stay", "hints")
    g.add_edge("hints", "costs")
    g.add_edge("costs", "budget_guard")
    g.add_conditional_edges("budget_guard", nodes.after_guard, {"transport": "transport", "itinerary": "itinerary"})
    g.add_edge("itinerary", "output")
    g.add_edge("output", END)
    return g.compile()


async def run_plan(
    request: TripRequest,
    llm: LlmProvider,
    *,
    max_loops: int,
    keep_days: list[LlmDay] | None = None,
    keep_title: str | None = None,
    keep_area: str | None = None,
    deadline: float | None = None,
    city_timeout: float | None = None,
) -> ItineraryResponse:
    """deadline is event-loop time (see deadline_after); past it, LLM steps fall back instead of waiting."""
    state = await get_graph().ainvoke(
        {
            "request": request,
            "llm": llm,
            "max_loops": max_loops,
            "keep_days": keep_days,
            "keep_title": keep_title,
            "keep_area": keep_area,
            "deadline": deadline,
            "city_timeout": city_timeout,
        },
        config={"recursion_limit": 60},
    )
    return state["result"]


def deadline_after(seconds: float) -> float:
    return asyncio.get_running_loop().time() + seconds
