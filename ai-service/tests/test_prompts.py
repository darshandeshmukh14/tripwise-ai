from dataclasses import replace
from datetime import date

from app.llm.base import DayPlanContext
from app.llm.prompts import day_plan_prompt

CTX = DayPlanContext(destination="Goa", origin="Mumbai", start_date=date(2026, 12, 20), days=3, travellers=2,
                     comfort_level="STANDARD", pace="balanced", interests=["beaches"], season="PEAK",
                     season_notes="", arrival_mode="TRAIN", budget_note="not specified")


def test_day_plan_prompt_asks_for_famous_spots_and_specific_places():
    prompt = day_plan_prompt(CTX)
    assert "most famous sights" in prompt
    assert "specific real place" in prompt
    assert "local food experience" in prompt
    assert "Return exactly 3 days" in prompt


def test_day_plan_prompt_activity_count_follows_pace():
    assert "4-5 activities per full day" in day_plan_prompt(replace(CTX, pace="packed"))
    assert "3 activities per full day" in day_plan_prompt(replace(CTX, pace="relaxed"))
