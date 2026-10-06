from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.analysis import AnalysisResult
from app.services.analysis.analysis_service import AnalysisService

router = APIRouter()


@router.post("/analysis", response_model=AnalysisResult)
async def analyze_file(file: UploadFile = File(...)):
    """Analyze a CSV or XLSX file."""

    if not file.filename:
        raise HTTPException(
            status_code=400,
            detail="Filename is required."
        )

    if not file.filename.lower().endswith((".csv", ".xlsx")):
        raise HTTPException(
            status_code=400,
            detail="Unsupported file type. Only CSV and XLSX are supported."
        )

    try:
        file_content = await file.read()

        return AnalysisService.analyze_file(
            file_content=file_content,
            filename=file.filename
        )

    except ValueError as exception:
        raise HTTPException(
            status_code=400,
            detail=str(exception)
        ) from exception

    except Exception as exception:
        raise HTTPException(
            status_code=500,
            detail="An error occurred while analyzing the file."
        ) from exception