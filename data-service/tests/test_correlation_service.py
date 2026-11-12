import pandas as pd
import pytest

from app.services.analysis.correlation_service import CorrelationService


def test_correlation_calculates_perfect_positive_relationship():
    df = pd.DataFrame({
        "x": [1, 2, 3, 4],
        "y": [2, 4, 6, 8],
    })

    result = CorrelationService.analyze(df)

    assert result["columns"] == ["x", "y"]
    assert result["pearson"][0][1] == pytest.approx(1.0)
    assert result["spearman"][0][1] == pytest.approx(1.0)


def test_correlation_calculates_perfect_negative_relationship():
    df = pd.DataFrame({
        "x": [1, 2, 3, 4],
        "y": [8, 6, 4, 2],
    })

    result = CorrelationService.analyze(df)

    assert result["pearson"][0][1] == pytest.approx(-1.0)
    assert result["spearman"][0][1] == pytest.approx(-1.0)


def test_correlation_handles_missing_values():
    df = pd.DataFrame({
        "x": [1, 2, 3, None],
        "y": [2, 4, 6, 8],
    })

    result = CorrelationService.analyze(df)

    assert result["observation_counts"][0][1] == 3
    assert result["pearson"][0][1] == pytest.approx(1.0)


def test_correlation_excludes_boolean_and_non_numeric_columns():
    df = pd.DataFrame({
        "x": [1, 2, 3],
        "y": [3, 2, 1],
        "active": [True, False, True],
        "name": ["A", "B", "C"],
    })

    result = CorrelationService.analyze(df)

    assert result["columns"] == ["x", "y"]


def test_correlation_requires_two_eligible_columns():
    df = pd.DataFrame({
        "x": [1, 2, 3],
        "name": ["A", "B", "C"],
    })

    result = CorrelationService.analyze(df)

    assert result["columns"] == ["x"]
    assert result["pearson"] == []
    assert result["spearman"] == []


def test_correlation_handles_constant_columns():
    df = pd.DataFrame({
        "constant": [5, 5, 5],
        "variable": [1, 2, 3],
    })

    result = CorrelationService.analyze(df)

    assert result["pearson"][0][1] is None
    assert result["spearman"][0][1] is None


def test_correlation_limits_numeric_columns():
    data = {
        f"column_{i}": [1, 2, 3]
        for i in range(CorrelationService.MAX_NUMERIC_COLUMNS + 2)
    }
    df = pd.DataFrame(data)

    result = CorrelationService.analyze(df)

    assert len(result["columns"]) == CorrelationService.MAX_NUMERIC_COLUMNS
    assert result["truncated"] is True


def test_correlation_excludes_infinite_values():
    df = pd.DataFrame({
        "x": [1, 2, float("inf"), 4, 5],
        "y": [2, 4, 6, float("-inf"), 10],
    })

    result = CorrelationService.analyze(df)

    assert result["observation_counts"][0][1] == 3
    assert result["pearson"][0][1] == pytest.approx(1.0)
    assert result["spearman"][0][1] == pytest.approx(1.0)


def test_correlation_counts_valid_observations_per_column_pair():
    df = pd.DataFrame({
        "x": [1, 2, 3, 4, None],
        "y": [2, 4, None, 8, 10],
        "z": [None, 4, 6, 8, 10],
    })

    result = CorrelationService.analyze(df)

    assert result["observation_counts"] == [
        [4, 3, 3],
        [3, 4, 3],
        [3, 3, 4],
    ]

    assert result["pearson"][0][1] == pytest.approx(1.0)
    assert result["pearson"][0][2] == pytest.approx(1.0)
    assert result["pearson"][1][2] == pytest.approx(1.0)
