from fastapi import APIRouter, File, HTTPException, UploadFile

from app.models.analysis import AnalysisResult
from app.services.analysis.analysis_service import AnalysisService

router = APIRouter()

MAX_FILE_SIZE = 10 * 1024 * 1024
CHUNK_SIZE = 1024 * 1024


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
        chunks = []
        total_size = 0

        while chunk := await file.read(CHUNK_SIZE):
            total_size += len(chunk)

            if total_size > MAX_FILE_SIZE:
                raise HTTPException(
                    status_code=413,
                    detail="File size exceeds the 10 MiB limit."
                )

            chunks.append(chunk)

        file_content = b"".join(chunks)

        return AnalysisService.analyze_file(
            file_content=file_content,
            filename=file.filename
        )

    except HTTPException:
        raise
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