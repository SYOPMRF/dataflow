
import re
from urllib.parse import urlsplit

import pandas as pd


class FormatValidationService:
    """Detect invalid formats in columns with recognized semantics."""

    EMAIL_COLUMNS = {
        "email",
        "e_mail",
        "correo",
        "correo_electronico",
    }

    URL_COLUMNS = {
        "url",
        "website",
        "web",
        "site_url",
    }

    DATE_COLUMNS = {
        "date",
        "delivery_date",
        "created_at",
        "updated_at",
        "fecha",
        "fecha_entrega",
        "fecha_creacion",
    }

    EMAIL_PATTERN = re.compile(
        r"^[^@\s]+@[^@\s]+\.[^@\s]+$"
    )

    @staticmethod
    def detect_issues(dataframe: pd.DataFrame) -> list[dict]:
        issues = []

        for column in dataframe.columns:
            normalized_name = (
                str(column)
                .strip()
                .casefold()
                .replace(" ", "_")
                .replace("-", "_")
            )

            if normalized_name in FormatValidationService.EMAIL_COLUMNS:
                validator = FormatValidationService._is_valid_email
            elif normalized_name in FormatValidationService.URL_COLUMNS:
                validator = FormatValidationService._is_valid_url
            elif normalized_name in FormatValidationService.DATE_COLUMNS:
                validator = FormatValidationService._is_valid_date
            else:
                continue

            values = dataframe[column]
            present_values = values[
                values.notna()
                & values.astype("string").str.strip().ne("").fillna(False)
            ]

            if present_values.empty:
                continue

            invalid_count = sum(
                not validator(str(value).strip())
                if not isinstance(value, pd.Timestamp)
                else not validator(value)
                for value in present_values
            )

            if invalid_count == 0:
                continue

            issues.append({
                "column_name": column,
                "issue_type": "INVALID_FORMAT",
                "severity": "MEDIUM",
                "message": (
                    f"{invalid_count} rows contain invalid values "
                    f"for column '{column}'."
                ),
                "related_columns": [column],
                "affected_rows": invalid_count,
            })

        return issues

    @staticmethod
    def _is_valid_email(value: str) -> bool:
        return bool(
            FormatValidationService.EMAIL_PATTERN.fullmatch(value)
        )

    @staticmethod
    def _is_valid_url(value: str) -> bool:
        if any(character.isspace() for character in value):
            return False

        try:
            parsed = urlsplit(value)

            if parsed.scheme.casefold() not in {"http", "https"}:
                return False

            if not parsed.hostname:
                return False

            _ = parsed.port

            return True
        except ValueError:
            return False

    @staticmethod
    def _is_valid_date(value) -> bool:
        if pd.api.types.is_number(value):
            return False

        try:
            parsed = pd.to_datetime(
                value,
                errors="coerce",
                format="mixed",
            )
            return not pd.isna(parsed)
        except (ValueError, TypeError, OverflowError):
            return False
