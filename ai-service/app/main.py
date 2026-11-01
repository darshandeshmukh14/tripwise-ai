import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse

from app.config import get_settings
from app.llm.base import LlmError
from app.llm.factory import get_llm
from app.routers import itineraries, preferences, stops
from app.security import require_internal_token

log = logging.getLogger("app")


@asynccontextmanager
async def lifespan(_: FastAPI):
    settings = get_settings()  # fail fast on missing secrets
    logging.basicConfig(level=settings.log_level.upper(),
                        format="%(asctime)s %(levelname)s %(name)s - %(message)s")
    get_llm()
    log.info("AI service started (provider=%s)", settings.llm_provider)
    yield


app = FastAPI(title="TripWise AI service", version="1.0.0", lifespan=lifespan,
              docs_url=None, redoc_url=None, openapi_url=None)

for router in (itineraries.router, preferences.router, stops.router):
    app.include_router(router, dependencies=[Depends(require_internal_token)])


@app.get("/health", include_in_schema=False)
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.exception_handler(LlmError)
async def llm_error(_: Request, exc: LlmError) -> JSONResponse:
    log.warning("LLM failure: %s", exc)
    return JSONResponse(status_code=502, content={"detail": "The AI planner could not complete the request"})


@app.exception_handler(Exception)
async def unexpected_error(_: Request, exc: Exception) -> JSONResponse:
    log.exception("Unhandled error")
    return JSONResponse(status_code=500, content={"detail": "Internal error"})
