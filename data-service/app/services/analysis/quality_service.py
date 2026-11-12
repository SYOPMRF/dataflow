class QualityService:
    """Service responsible for detecting data quality issues."""

    @staticmethod
    def detect_issues(columns: list[dict]) -> list[dict]:
        """Detect basic data quality issues from column analysis."""
        issues = []

        for column in columns:
            column_name = column["name"]
            null_count = int(column["null_count"])
            negative_count = int(column["negative_count"])

            if column["is_completely_empty"]:
                issues.append({
                    "column_name": column_name,
                    "issue_type": "COMPLETELY_EMPTY",
                    "severity": "HIGH",
                    "message": f"Column '{column_name}' is completely empty.",
                    "related_columns": [],
                    "affected_rows": null_count,
                })

            elif column["missing_percentage"] >= 50:
                issues.append({
                    "column_name": column_name,
                    "issue_type": "HIGH_MISSING_VALUES",
                    "severity": "HIGH",
                    "message": (
                        f"Column '{column_name}' has "
                        f"{column['missing_percentage']:.1f}% missing values."
                    ),
                    "related_columns": [],
                    "affected_rows": null_count,
                })

            elif column["missing_percentage"] > 0:
                issues.append({
                    "column_name": column_name,
                    "issue_type": "MISSING_VALUES",
                    "severity": "MEDIUM",
                    "message": (
                        f"Column '{column_name}' has "
                        f"{column['missing_percentage']:.1f}% missing values."
                    ),
                    "related_columns": [],
                    "affected_rows": null_count,
                })

            if negative_count > 0:
                issues.append({
                    "column_name": column_name,
                    "issue_type": "NEGATIVE_VALUES",
                    "severity": "MEDIUM",
                    "message": (
                        f"Column '{column_name}' contains "
                        f"{negative_count} negative values."
                    ),
                    "related_columns": [],
                    "affected_rows": negative_count,
                })

        return issues
