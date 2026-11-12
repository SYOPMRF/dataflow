from io import BytesIO

import numpy as np
import pandas as pd

from app.services.analysis.quality_service import QualityService
from app.services.analysis.duplicate_service import DuplicateService
from app.services.analysis.distribution_service import DistributionService
from app.services.analysis.correlation_service import CorrelationService
from app.services.analysis.semantic_validation_service import (
    SemanticValidationService,
)
from app.services.analysis.format_validation_service import (
    FormatValidationService,
)


class AnalysisService:
    """Service responsible for analyzing CSV and XLSX files."""

    @staticmethod
    def load_dataframe(file_content: bytes, filename: str) -> pd.DataFrame:
        """Load a CSV or XLSX file into a pandas DataFrame."""

        if not file_content:
            raise ValueError("The uploaded file is empty.")

        if filename.lower().endswith(".csv"):
            return pd.read_csv(BytesIO(file_content))

        if filename.lower().endswith(".xlsx"):
            return pd.read_excel(BytesIO(file_content))

        raise ValueError(
            "Unsupported file type. Only CSV and XLSX are supported.")

    @staticmethod
    def get_data_type(series: pd.Series) -> str:
        """Return a user-friendly data type for a pandas Series."""

        if pd.api.types.is_bool_dtype(series):
            return "boolean"

        if pd.api.types.is_integer_dtype(series):
            return "integer"

        if pd.api.types.is_float_dtype(series):
            return "decimal"

        if pd.api.types.is_datetime64_any_dtype(series):
            return "datetime"

        if pd.api.types.is_object_dtype(series) or pd.api.types.is_string_dtype(series):
            non_null_values = series.dropna()

            if not non_null_values.empty:
                parsed_dates = pd.to_datetime(
                    non_null_values,
                    errors="coerce",
                    format="mixed"
                )

                if parsed_dates.notna().all():
                    return "date"

            return "text"

        return "unknown"

    @staticmethod
    def analyze_dataframe(df: pd.DataFrame) -> dict:
        """Generate basic data quality information from a DataFrame."""

        missing_values = int(df.isna().sum().sum())
        duplicate_rows = int(df.duplicated().sum())

        columns = []

        for column in df.columns:
            series = df[column]

            null_count = int(series.isna().sum())

            missing_percentage = (
                (null_count / len(df)) * 100
                if len(df) > 0
                else 0.0
            )

            unique_count = int(series.nunique(dropna=True))

            unique_percentage = (
                (unique_count / len(df)) * 100
                if len(df) > 0
                else 0.0
            )

            duplicate_count = int(series.duplicated().sum())

            duplicated_unique_count = int(
                series.value_counts(dropna=True).gt(1).sum()
            )

            is_completely_empty = len(df) > 0 and null_count == len(df)

            column_analysis = {
                "name": str(column),
                "data_type": AnalysisService.get_data_type(series),
                "null_count": null_count,
                "unique_count": unique_count,
                "unique_percentage": unique_percentage,
                "duplicate_count": duplicate_count,
                "duplicated_unique_count": duplicated_unique_count,
                "missing_percentage": missing_percentage,
                "is_completely_empty": is_completely_empty,
                "min_value": None,
                "max_value": None,
                "mean_value": None,
                "median_value": None,
                "std_deviation": None,
                "q1_value": None,
                "q3_value": None,
                "iqr_value": None,
                "p5_value": None,
                "p10_value": None,
                "p90_value": None,
                "p95_value": None,
                "outlier_count": None,
                "z_score_outlier_count": None,
                "zero_count": 0,
                "negative_count": 0,
            }

            if pd.api.types.is_numeric_dtype(series) and not pd.api.types.is_bool_dtype(series):
                numeric_values = pd.to_numeric(series, errors="coerce")
                valid_values = numeric_values[np.isfinite(numeric_values)]

                column_analysis["zero_count"] = int((valid_values == 0).sum())
                column_analysis["negative_count"] = int(
                    (valid_values < 0).sum())

                if not valid_values.empty:
                    percentiles = valid_values.quantile(
                        [0.05, 0.10, 0.25, 0.50, 0.75, 0.90, 0.95]
                    )

                    q1 = float(percentiles.loc[0.25])
                    q3 = float(percentiles.loc[0.75])
                    iqr = q3 - q1

                    lower_bound = q1 - (1.5 * iqr)
                    upper_bound = q3 + (1.5 * iqr)

                    column_analysis["min_value"] = float(valid_values.min())
                    column_analysis["max_value"] = float(valid_values.max())
                    column_analysis["mean_value"] = float(valid_values.mean())
                    column_analysis["median_value"] = float(
                        percentiles.loc[0.50])
                    column_analysis["std_deviation"] = (
                        float(valid_values.std())
                        if len(valid_values) > 1
                        else 0.0
                    )

                    column_analysis["q1_value"] = q1
                    column_analysis["q3_value"] = q3
                    column_analysis["iqr_value"] = iqr
                    column_analysis["p5_value"] = float(percentiles.loc[0.05])
                    column_analysis["p10_value"] = float(percentiles.loc[0.10])
                    column_analysis["p90_value"] = float(percentiles.loc[0.90])
                    column_analysis["p95_value"] = float(percentiles.loc[0.95])

                    outliers = (
                        (valid_values < lower_bound)
                        | (valid_values > upper_bound)
                    )

                    std_deviation = float(valid_values.std(ddof=0))

                    if std_deviation > 0:
                        z_scores = (
                            (valid_values - valid_values.mean()) / std_deviation
                        )
                        column_analysis["z_score_outlier_count"] = int(
                            (z_scores.abs() > 3).sum()
                        )
                    else:
                        column_analysis["z_score_outlier_count"] = 0

                    column_analysis["outlier_count"] = int(outliers.sum())
                else:
                    column_analysis["outlier_count"] = 0
                    column_analysis["z_score_outlier_count"] = 0

            columns.append(column_analysis)

        quality_issues = QualityService.detect_issues(columns)

        quality_issues.extend(
            SemanticValidationService.detect_issues(df)
        )

        quality_issues.extend(
            FormatValidationService.detect_issues(df)
        )

        return {
            "row_count": int(len(df)),
            "column_count": int(len(df.columns)),
            "missing_count": missing_values,
            "duplicate_count": duplicate_rows,
            "columns": columns,
            "quality_issues": quality_issues,
            "duplicate_analysis": DuplicateService.analyze(df),
            "distribution_analysis": DistributionService.analyze(df),
            "correlation_analysis": CorrelationService.analyze(df),
        }

    @classmethod
    def analyze_file(cls, file_content: bytes, filename: str) -> dict:
        """Load and analyze a CSV or XLSX file."""

        dataframe = cls.load_dataframe(file_content, filename)

        return cls.analyze_dataframe(dataframe)
