from itertools import combinations

import numpy as np
import pandas as pd


class DuplicateService:
    """Detect candidate keys and repeated value combinations."""
    MAX_LOW_CARDINALITY_COLUMNS = 4
    MIN_EVALUABLE_ROW_PERCENTAGE = 80.0
    MAX_CANDIDATE_COLUMNS = 12
    MAX_EVALUATED_COMBINATIONS = 500
    MAX_DUPLICATE_GROUPS = 20

    @staticmethod
    def _serialize_value(value):
        """Convert pandas and NumPy values into JSON-compatible values."""
        if isinstance(value, np.generic):
            value = value.item()
        if isinstance(value, (pd.Timestamp, pd.Timedelta)):
            return value.isoformat() if isinstance(value, pd.Timestamp) else str(value)
        if isinstance(value, float) and not np.isfinite(value):
            return None
        if value is None or isinstance(value, (str, int, float, bool)):
            return value

        return str(value)

    @classmethod
    def _select_candidate_columns(cls, df: pd.DataFrame) -> list:
        """Select a balanced and deterministic set of candidate columns."""
        total_rows = len(df)

        candidates = []

        for column in df.columns:
            unique_count = df[column].nunique(dropna=True)

            if unique_count < 2:
                continue

            non_null_percentage = (
                df[column].notna().sum() / total_rows * 100
            )

            cardinality_percentage = (
                unique_count / total_rows * 100
            )

            candidates.append({
                "column": column,
                "unique_count": unique_count,
                "non_null_percentage": non_null_percentage,
                "cardinality_percentage": cardinality_percentage,
            })

        if len(candidates) <= cls.MAX_CANDIDATE_COLUMNS:
            return [item["column"] for item in candidates]

        low_cardinality = [
            item
            for item in candidates
            if item["cardinality_percentage"] < 80
        ]

        high_cardinality = [
            item
            for item in candidates
            if item["cardinality_percentage"] >= 80
        ]

        low_cardinality.sort(
            key=lambda item: (
                -item["non_null_percentage"],
                -item["unique_count"],
                str(item["column"]),
            )
        )

        high_cardinality.sort(
            key=lambda item: (
                -item["non_null_percentage"],
                -item["unique_count"],
                str(item["column"]),
            )
        )

        selected = low_cardinality[
            :cls.MAX_LOW_CARDINALITY_COLUMNS
        ]

        remaining_slots = cls.MAX_CANDIDATE_COLUMNS - len(selected)

        selected.extend(high_cardinality[:remaining_slots])

        if len(selected) < cls.MAX_CANDIDATE_COLUMNS:
            selected_columns = {
                item["column"] for item in selected
            }

            remaining = [
                item
                for item in low_cardinality[
                    cls.MAX_LOW_CARDINALITY_COLUMNS:
                ]
                if item["column"] not in selected_columns
            ]

            selected.extend(
                remaining[:cls.MAX_CANDIDATE_COLUMNS - len(selected)]
            )

        return [item["column"] for item in selected]

    @classmethod
    def analyze(cls, df: pd.DataFrame) -> dict:
        """Find candidate keys and repeated value combinations."""
        result = {
            "candidate_keys": [],
            "duplicate_groups": [],
            "evaluated_combinations": 0,
            "columns_truncated": False,
            "combinations_truncated": False,
            "duplicate_groups_truncated": False,
            "truncated": False,
        }

        if df.empty or df.columns.empty:
            return result

        total_rows = len(df)

        candidate_columns = cls._select_candidate_columns(df)

        eligible_columns = sum(
            df[column].nunique(dropna=True) >= 2
            for column in df.columns
        )

        if eligible_columns > cls.MAX_CANDIDATE_COLUMNS:
            result["columns_truncated"] = True
            result["truncated"] = True

        combinations_to_evaluate = [
            (column,)
            for column in candidate_columns
        ]

        combinations_to_evaluate.extend(
            combinations(candidate_columns, 2)
        )

        combinations_to_evaluate.extend(
            combinations(candidate_columns, 3)
        )

        if len(combinations_to_evaluate) > cls.MAX_EVALUATED_COMBINATIONS:
            combinations_to_evaluate = combinations_to_evaluate[
                :cls.MAX_EVALUATED_COMBINATIONS
            ]
            result["combinations_truncated"] = True
            result["truncated"] = True

        for columns in combinations_to_evaluate:
            result["evaluated_combinations"] += 1

            subset = df[list(columns)].dropna()
            evaluated_rows = len(subset)

            if evaluated_rows == 0:
                continue

            counts = subset.value_counts(sort=True, dropna=True)
            unique_count = len(counts)

            evaluable_percentage = (
                evaluated_rows / total_rows * 100
            )

            key_type = None

            if len(columns) == 1:
                has_no_nulls = evaluated_rows == total_rows

                if has_no_nulls and unique_count == evaluated_rows:
                    key_type = "SINGLE"

            elif (
                evaluable_percentage
                >= cls.MIN_EVALUABLE_ROW_PERCENTAGE
                and unique_count == evaluated_rows
            ):
                key_type = "COMPOSITE"

            if key_type is not None and len(columns) <= 2:
                column_set = set(columns)

                has_candidate_subset = any(
                    set(candidate["columns"]).issubset(column_set)
                    for candidate in result["candidate_keys"]
                )

                if not has_candidate_subset:
                    result["candidate_keys"].append({
                        "columns": list(columns),
                        "key_type": key_type,
                        "evaluated_row_count": evaluated_rows,
                        "unique_count": unique_count,
                        "uniqueness_percentage": 100.0,
                    })

            if len(columns) > 1:
                continue

            repeated_values = counts[counts > 1]

            if repeated_values.empty:
                continue

            duplicate_count = int((repeated_values - 1).sum())
            max_frequency = int(repeated_values.max())

            examples = []

            for values, frequency in repeated_values.head(
                cls.MAX_DUPLICATE_GROUPS
            ).items():
                if len(columns) == 1:
                    values = values if isinstance(values, tuple) else (values,)

                examples.append({
                    "values": [
                        cls._serialize_value(value)
                        for value in values
                    ],
                    "frequency": int(frequency),
                })

            if len(repeated_values) > cls.MAX_DUPLICATE_GROUPS:
                result["duplicate_groups_truncated"] = True
                result["truncated"] = True

            result["duplicate_groups"].append({
                "columns": list(columns),
                "group_count": len(repeated_values),
                "duplicate_count": duplicate_count,
                "max_frequency": max_frequency,
                "examples": examples,
            })

        return result
