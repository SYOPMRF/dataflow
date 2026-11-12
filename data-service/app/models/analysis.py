from pydantic import BaseModel, Field


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
    q1_value: float | None = None
    q3_value: float | None = None
    iqr_value: float | None = None
    p5_value: float | None = None
    p10_value: float | None = None
    p90_value: float | None = None
    p95_value: float | None = None
    outlier_count: int | None = None
    z_score_outlier_count: int | None = None
    zero_count: int = 0
    negative_count: int = 0


class DataQualityIssue(BaseModel):

    column_name: str

    issue_type: str

    severity: str

    message: str

    related_columns: list[str] = Field(default_factory=list)

    affected_rows: int = Field(default=0, ge=0)


class CandidateKey(BaseModel):
    columns: list[str]
    key_type: str
    evaluated_row_count: int
    unique_count: int
    uniqueness_percentage: float


class DuplicateValueExample(BaseModel):
    values: list[str | int | float | bool | None]
    frequency: int


class DuplicateGroup(BaseModel):
    columns: list[str]
    group_count: int
    duplicate_count: int
    max_frequency: int
    examples: list[DuplicateValueExample] = Field(
        default_factory=list
    )


class DuplicateAnalysis(BaseModel):

    candidate_keys: list[CandidateKey] = Field(default_factory=list)

    duplicate_groups: list[DuplicateGroup] = Field(default_factory=list)

    evaluated_combinations: int = 0

    columns_truncated: bool = False

    combinations_truncated: bool = False

    duplicate_groups_truncated: bool = False

    truncated: bool = False


class DistributionBin(BaseModel):
    lower_bound: float
    upper_bound: float
    count: int
    percentage: float


class DistributionCategory(BaseModel):
    value: str | int | float | bool | None
    count: int
    percentage: float


class ColumnDistribution(BaseModel):
    column: str
    distribution_type: str
    valid_count: int
    null_count: int

    bin_count: int | None = None
    bins: list[DistributionBin] = Field(default_factory=list)

    category_count: int | None = None
    categories: list[DistributionCategory] = Field(
        default_factory=list
    )
    categories_truncated: bool = False


class DistributionAnalysis(BaseModel):
    columns: list[ColumnDistribution] = Field(default_factory=list)


class CorrelationAnalysis(BaseModel):
    columns: list[str] = Field(default_factory=list)
    pearson: list[list[float | None]] = Field(default_factory=list)
    spearman: list[list[float | None]] = Field(default_factory=list)
    observation_counts: list[list[int]] = Field(default_factory=list)
    truncated: bool = False


class AnalysisResult(BaseModel):
    row_count: int
    column_count: int
    missing_count: int
    duplicate_count: int
    columns: list[ColumnAnalysis]
    quality_issues: list[DataQualityIssue]
    duplicate_analysis: DuplicateAnalysis = Field(
        default_factory=DuplicateAnalysis
    )
    distribution_analysis: DistributionAnalysis = Field(
        default_factory=DistributionAnalysis
    )

    correlation_analysis: CorrelationAnalysis = Field(
        default_factory=CorrelationAnalysis
    )
