from io import BytesIO

import pandas as pd
from app.services.analysis.quality_service import QualityService


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

        raise ValueError("Unsupported file type. Only CSV and XLSX are supported.")

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
                "zero_count": 0,
                "negative_count": 0,
            }

            if pd.api.types.is_numeric_dtype(series):
                column_analysis["min_value"] = (
                    float(series.min()) if not series.dropna().empty else None
                )
                column_analysis["max_value"] = (
                    float(series.max()) if not series.dropna().empty else None
                )
                column_analysis["mean_value"] = (
                    float(series.mean()) if not series.dropna().empty else None
                )
                column_analysis["median_value"] = (
                    float(series.median()) if not series.dropna().empty else None
                )
                column_analysis["std_deviation"] = (
                    float(series.std()) if not series.dropna().empty else None
                )
                column_analysis["zero_count"] = int((series == 0).sum())
                column_analysis["negative_count"] = int((series < 0).sum())

            columns.append(column_analysis)

        quality_issues = QualityService.detect_issues(columns)

        return {
            "row_count": int(len(df)),
            "column_count": int(len(df.columns)),
            "missing_count": missing_values,
            "duplicate_count": duplicate_rows,
            "columns": columns,
            "quality_issues": quality_issues,
        }

    @classmethod
    def analyze_file(cls, file_content: bytes, filename: str) -> dict:
        """Load and analyze a CSV or XLSX file."""

        dataframe = cls.load_dataframe(file_content, filename)

        return cls.analyze_dataframe(dataframe)