from pydantic import BaseModel


class ColumnAnalysis(BaseModel):
    name: str
    data_type: str
    null_count: int
    unique_count: int
    unique_percentage: float
    duplicate_count: int
    duplicated_unique_count: int
    missing_percentage: float
    is_completely_empty: bool
    min_value: float | int | str | None = None
    max_value: float | int | str | None = None
    mean_value: float | None = None
    median_value: float | None = None
    std_deviation: float | None = None
    zero_count: int = 0
    negative_count: int = 0

class DataQualityIssue(BaseModel):
    column_name: str
    issue_type: str
    severity: str
    message: str

class AnalysisResult(BaseModel):
    row_count: int
    column_count: int
    missing_count: int
    duplicate_count: int
    columns: list[ColumnAnalysis]
    quality_issues: list[DataQualityIssue]