from fastapi import FastAPI

from app.api.routes.analysis import router as analysis_router
from app.api.routes.health import router as health_router

app = FastAPI(
    title="DataFlow Data Service",
    description="Data processing and analysis service for DataFlow",
    version="0.1.0"
)

app.include_router(health_router)
app.include_router(analysis_router)