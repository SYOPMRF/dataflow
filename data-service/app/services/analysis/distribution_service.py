import numpy as np
import pandas as pd


class DistributionService:
    """Generate bounded numeric and categorical distributions."""

    MAX_BINS = 20
    MAX_CATEGORIES = 15
    MIN_NUMERIC_VALUES = 2

    @classmethod
    def analyze(cls, df: pd.DataFrame) -> dict:
        """Analyze distributions for every applicable column."""
        distributions = []

        for column in df.columns:
            series = df[column]

            if (
                pd.api.types.is_numeric_dtype(series)
                and not pd.api.types.is_bool_dtype(series)
            ):
                distribution = cls._numeric_distribution(
                    str(column), series
                )
            else:
                distribution = cls._categorical_distribution(
                    str(column), series
                )

            if distribution is not None:
                distributions.append(distribution)

        return {"columns": distributions}

    @classmethod
    def _numeric_distribution(
        cls, column: str, series: pd.Series
    ) -> dict | None:
        """Build a histogram using automatically determined intervals."""
        values = pd.to_numeric(series, errors="coerce")
        values = values[np.isfinite(values)]

        if len(values) < cls.MIN_NUMERIC_VALUES:
            return None

        minimum = float(values.min())
        maximum = float(values.max())

        if minimum == maximum:
            return {
                "column": column,
                "distribution_type": "numeric",
                "valid_count": int(len(values)),
                "null_count": int(len(series) - len(values)),
                "bin_count": 1,
                "bins": [{
                    "lower_bound": minimum,
                    "upper_bound": maximum,
                    "count": int(len(values)),
                    "percentage": 100.0,
                }],
            }

        counts, edges = np.histogram(
            values.to_numpy(dtype=float),
            bins="auto",
        )

        if len(counts) > cls.MAX_BINS:
            counts, edges = np.histogram(
                values.to_numpy(dtype=float),
                bins=cls.MAX_BINS,
            )

        bins = [
            {
                "lower_bound": float(edges[index]),
                "upper_bound": float(edges[index + 1]),
                "count": int(count),
                "percentage": float(count / len(values) * 100),
            }
            for index, count in enumerate(counts)
        ]

        return {
            "column": column,
            "distribution_type": "numeric",
            "valid_count": int(len(values)),
            "null_count": int(series.isna().sum()),
            "bin_count": len(bins),
            "bins": bins,
        }

    @classmethod
    def _categorical_distribution(
        cls, column: str, series: pd.Series
    ) -> dict | None:
        """Build a bounded frequency distribution for categories."""
        values = series.dropna()

        if values.empty:
            return None

        counts = values.value_counts(dropna=True)
        total_count = int(len(values))

        top_categories = counts.head(cls.MAX_CATEGORIES)
        categories = [
            {
                "value": cls._serialize_value(value),
                "count": int(count),
                "percentage": float(count / total_count * 100),
            }
            for value, count in top_categories.items()
        ]

        omitted_count = int(counts.iloc[cls.MAX_CATEGORIES:].sum())

        if omitted_count:
            categories.append({
                "value": "Other",
                "count": omitted_count,
                "percentage": float(
                    omitted_count / total_count * 100
                ),
            })

        return {
            "column": column,
            "distribution_type": "categorical",
            "valid_count": total_count,
            "null_count": int(series.isna().sum()),
            "category_count": int(len(counts)),
            "categories": categories,
            "categories_truncated": omitted_count > 0,
        }

    @staticmethod
    def _serialize_value(value):
        """Convert pandas and NumPy values into JSON-compatible values."""
        if isinstance(value, np.generic):
            value = value.item()

        if isinstance(value, (pd.Timestamp, pd.Timedelta)):
            return (
                value.isoformat()
                if isinstance(value, pd.Timestamp)
                else str(value)
            )

        if isinstance(value, float) and not np.isfinite(value):
            return None

        if value is None or isinstance(
            value, (str, int, float, bool)
        ):
            return value

        return str(value)
