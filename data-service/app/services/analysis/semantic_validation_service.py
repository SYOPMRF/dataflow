import numpy as np
import pandas as pd


class SemanticValidationService:
    """Detect semantic inconsistencies between related DataFrame columns."""

    CURRENCY_TOLERANCE = 0.01

    @staticmethod
    def detect_issues(dataframe: pd.DataFrame) -> list[dict]:
        """Detect ecommerce-related semantic validation issues."""

        issues = []

        issues.extend(
            SemanticValidationService._detect_delivered_without_delivery_date(
                dataframe
            )
        )

        issues.extend(
            SemanticValidationService._detect_invalid_discounts(dataframe)
        )

        issues.extend(
            SemanticValidationService._detect_inconsistent_order_totals(
                dataframe
            )
        )

        return issues

    @staticmethod
    def _detect_delivered_without_delivery_date(
        dataframe: pd.DataFrame,
    ) -> list[dict]:
        required_columns = {"order_status", "delivery_date"}

        if not required_columns.issubset(dataframe.columns):
            return []

        delivered = (
            dataframe["order_status"]
            .astype("string")
            .str.strip()
            .str.casefold()
            .eq("delivered")
            .fillna(False)
        )

        missing_delivery_date = dataframe["delivery_date"].isna()

        affected_rows = int(
            (delivered & missing_delivery_date).sum()
        )

        if affected_rows == 0:
            return []

        return [{
            "column_name": "delivery_date",
            "issue_type": "DELIVERED_WITHOUT_DELIVERY_DATE",
            "severity": "HIGH",
            "message": (
                f"{affected_rows} delivered orders have no delivery date."
            ),
            "related_columns": [
                "order_status",
                "delivery_date",
            ],
            "affected_rows": affected_rows,
        }]

    @staticmethod
    def _detect_invalid_discounts(
        dataframe: pd.DataFrame,
    ) -> list[dict]:
        column = "discount_pct"

        if column not in dataframe.columns:
            return []

        numeric_values = pd.to_numeric(
            dataframe[column],
            errors="coerce",
        )

        valid_values = numeric_values[
            np.isfinite(numeric_values)
        ]

        invalid = (
            (valid_values < 0)
            | (valid_values > 100)
        )

        affected_rows = int(invalid.sum())

        if affected_rows == 0:
            return []

        return [{
            "column_name": column,
            "issue_type": "INVALID_DISCOUNT",
            "severity": "HIGH",
            "message": (
                f"{affected_rows} rows contain discounts outside 0-100%."
            ),
            "related_columns": [column],
            "affected_rows": affected_rows,
        }]

    @staticmethod
    def _detect_inconsistent_order_totals(
        dataframe: pd.DataFrame,
    ) -> list[dict]:
        required_columns = {
            "quantity",
            "unit_price",
            "discount_pct",
            "total_amount",
        }

        if not required_columns.issubset(dataframe.columns):
            return []

        numeric_columns = {}

        for column in required_columns:
            numeric_columns[column] = pd.to_numeric(
                dataframe[column],
                errors="coerce",
            )

        valid = pd.Series(True, index=dataframe.index)

        for values in numeric_columns.values():
            valid &= np.isfinite(values)

        valid &= numeric_columns["discount_pct"].between(0, 100)
        valid &= numeric_columns["quantity"] >= 0
        valid &= numeric_columns["unit_price"] >= 0
        valid &= numeric_columns["total_amount"] >= 0

        expected_total = (
            numeric_columns["quantity"]
            * numeric_columns["unit_price"]
            * (1 - numeric_columns["discount_pct"] / 100)
        )

        valid &= np.isfinite(expected_total)

        difference = (
            numeric_columns["total_amount"] - expected_total
        ).abs()

        inconsistent = (
            valid
            & (
                difference
                > SemanticValidationService.CURRENCY_TOLERANCE + 1e-9
            )
        )

        affected_rows = int(inconsistent.sum())

        if affected_rows == 0:
            return []

        return [{
            "column_name": "total_amount",
            "issue_type": "INCONSISTENT_ORDER_TOTAL",
            "severity": "MEDIUM",
            "message": (
                f"{affected_rows} rows have totals inconsistent "
                "with quantity, unit price and discount."
            ),
            "related_columns": [
                "quantity",
                "unit_price",
                "discount_pct",
                "total_amount",
            ],
            "affected_rows": affected_rows,
        }]
