import pandas as pd

from app.services.analysis.distribution_service import DistributionService


def test_numeric_distribution_generates_histogram():
    df = pd.DataFrame({"age": [10, 20, 30, 40]})

    result = DistributionService.analyze(df)

    distribution = result["columns"][0]

    assert distribution["column"] == "age"
    assert distribution["distribution_type"] == "numeric"
    assert distribution["valid_count"] == 4
    assert sum(item["count"] for item in distribution["bins"]) == 4
    assert distribution["bin_count"] <= DistributionService.MAX_BINS


def test_numeric_distribution_handles_constant_values():
    df = pd.DataFrame({"score": [5, 5, 5]})

    result = DistributionService.analyze(df)
    distribution = result["columns"][0]

    assert distribution["bin_count"] == 1
    assert distribution["bins"][0]["count"] == 3
    assert distribution["bins"][0]["percentage"] == 100.0


def test_numeric_distribution_excludes_non_finite_values():
    df = pd.DataFrame({"value": [1.0, 2.0, float("inf"), float("-inf")]})

    result = DistributionService.analyze(df)
    distribution = result["columns"][0]

    assert distribution["valid_count"] == 2
    assert sum(item["count"] for item in distribution["bins"]) == 2


def test_categorical_distribution_limits_categories():
    df = pd.DataFrame({
        "category": [f"category_{i}" for i in range(20)]
    })

    result = DistributionService.analyze(df)
    distribution = result["columns"][0]

    assert distribution["distribution_type"] == "categorical"
    assert distribution["categories_truncated"] is True
    assert len(distribution["categories"]) <= (
        DistributionService.MAX_CATEGORIES + 1
    )
    assert sum(item["count"] for item in distribution["categories"]) == 20


def test_categorical_distribution_handles_null_values():
    df = pd.DataFrame({"category": ["A", "B", None, "A"]})

    result = DistributionService.analyze(df)
    distribution = result["columns"][0]

    assert distribution["valid_count"] == 3
    assert distribution["null_count"] == 1


def test_distribution_skips_numeric_columns_with_insufficient_values():
    df = pd.DataFrame({"value": [10]})

    result = DistributionService.analyze(df)

    assert result["columns"] == []


def test_distribution_skips_empty_columns():
    df = pd.DataFrame({"category": [None, None]})

    result = DistributionService.analyze(df)

    assert result["columns"] == []
