import numpy as np
import pandas as pd


class CorrelationService:
    """Calculate bounded Pearson and Spearman correlation matrices."""

    MIN_OBSERVATIONS = 3
    MAX_NUMERIC_COLUMNS = 30

    @classmethod
    def analyze(cls, df: pd.DataFrame) -> dict:
        """Generate correlation matrices for applicable numeric columns."""
        numeric_columns = [
            column
            for column in df.columns
            if (
                pd.api.types.is_numeric_dtype(df[column])
                and not pd.api.types.is_bool_dtype(df[column])
            )
        ]

        eligible_columns = [
            column
            for column in numeric_columns
            if np.isfinite(
                pd.to_numeric(df[column], errors="coerce")
            ).sum() >= cls.MIN_OBSERVATIONS
        ]

        truncated = len(eligible_columns) > cls.MAX_NUMERIC_COLUMNS
        selected_columns = eligible_columns[:cls.MAX_NUMERIC_COLUMNS]

        if len(selected_columns) < 2:
            return {
                "columns": [str(column) for column in selected_columns],
                "pearson": [],
                "spearman": [],
                "observation_counts": [],
                "truncated": truncated,
            }

        numeric_df = pd.DataFrame({
            column: pd.to_numeric(df[column], errors="coerce")
            for column in selected_columns
        })

        numeric_df = numeric_df.replace(
            [np.inf, -np.inf], np.nan
        )

        pearson = numeric_df.corr(
            method="pearson",
            min_periods=cls.MIN_OBSERVATIONS,
        )

        spearman = numeric_df.corr(
            method="spearman",
            min_periods=cls.MIN_OBSERVATIONS,
        )

        observation_counts = numeric_df.notna().astype(int).T.dot(
            numeric_df.notna().astype(int)
        )

        return {
            "columns": [str(column) for column in selected_columns],
            "pearson": cls._serialize_matrix(pearson),
            "spearman": cls._serialize_matrix(spearman),
            "observation_counts": observation_counts.astype(int).values.tolist(),
            "truncated": truncated,
        }

    @staticmethod
    def _serialize_matrix(matrix: pd.DataFrame) -> list:
        """Convert correlation values into JSON-compatible numbers."""
        return [
            [
                float(value) if pd.notna(value) and np.isfinite(value)
                else None
                for value in row
            ]
            for row in matrix.to_numpy()
        ]
