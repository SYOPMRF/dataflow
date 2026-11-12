import numpy as np
import pandas as pd

from app.services.analysis.duplicate_service import DuplicateService


def test_detects_single_column_candidate_key():
    dataframe = pd.DataFrame({
        "customer_id": [101, 102, 103, 104],
        "name": ["Ana", "Luis", "Ana", "Luis"],
    })

    result = DuplicateService.analyze(dataframe)

    assert {
        "columns": ["customer_id"],
        "key_type": "SINGLE",
        "evaluated_row_count": 4,
        "unique_count": 4,
        "uniqueness_percentage": 100.0,
    } in result["candidate_keys"]


def test_detects_composite_candidate_key():
    dataframe = pd.DataFrame({
        "customer_id": [1, 1, 2, 2],
        "order_id": [101, 102, 101, 102],
        "region": ["North", "North", "South", "South"],
    })

    result = DuplicateService.analyze(dataframe)

    assert {
        "columns": ["customer_id", "order_id"],
        "key_type": "COMPOSITE",
        "evaluated_row_count": 4,
        "unique_count": 4,
        "uniqueness_percentage": 100.0,
    } in result["candidate_keys"]


def test_does_not_detect_single_key_with_null_values():
    dataframe = pd.DataFrame({
        "customer_id": [101, 102, None, 104],
    })

    result = DuplicateService.analyze(dataframe)

    assert result["candidate_keys"] == []


def test_does_not_report_repeated_composite_values():

    dataframe = pd.DataFrame({

        "product": ["Laptop", "Laptop", "Laptop", "Phone", "Phone"],

        "region": ["North", "North", "North", "South", "South"],

        "category": ["A", "A", "A", "B", "B"],

    })

    result = DuplicateService.analyze(dataframe)

    assert result["duplicate_groups"]

    assert all(
        len(group["columns"]) == 1
        for group in result["duplicate_groups"]
    )


def test_ignores_null_rows_when_evaluating_composite_keys():
    dataframe = pd.DataFrame({
        "customer_id": [1, 1, 2, 2, None],
        "order_id": [101, 102, 101, 102, 105],
    })

    result = DuplicateService.analyze(dataframe)

    candidate = next(
        item
        for item in result["candidate_keys"]
        if item["columns"] == ["customer_id", "order_id"]
    )

    assert candidate["evaluated_row_count"] == 4
    assert candidate["unique_count"] == 4


def test_does_not_evaluate_composite_key_with_too_many_null_rows():
    dataframe = pd.DataFrame({
        "customer_id": [1, 1, None, None, None],
        "order_id": [101, 102, 103, 104, 105],
    })

    result = DuplicateService.analyze(dataframe)

    assert not any(
        item["columns"] == ["customer_id", "order_id"]
        for item in result["candidate_keys"]
    )


def test_returns_empty_result_for_empty_dataframe():
    result = DuplicateService.analyze(pd.DataFrame())

    assert result == {
        "candidate_keys": [],
        "duplicate_groups": [],
        "evaluated_combinations": 0,
        "columns_truncated": False,
        "combinations_truncated": False,
        "duplicate_groups_truncated": False,
        "truncated": False,
    }


def test_limits_candidate_columns():
    dataframe = pd.DataFrame({
        f"id_{index}": range(10)
        for index in range(13)
    })

    result = DuplicateService.analyze(dataframe)

    assert result["truncated"] is True
    assert result["evaluated_combinations"] <= 500
    assert result["columns_truncated"] is True
    assert result["combinations_truncated"] is False
    assert result["duplicate_groups_truncated"] is False


def test_limits_duplicate_groups_to_twenty_examples():
    values = [
        value
        for index in range(21)
        for value in [f"value_{index}", f"value_{index}"]
    ]

    dataframe = pd.DataFrame({
        "product": values,
    })

    result = DuplicateService.analyze(dataframe)

    group = next(
        item
        for item in result["duplicate_groups"]
        if item["columns"] == ["product"]
    )

    assert group["group_count"] == 21
    assert group["duplicate_count"] == 21
    assert len(group["examples"]) == 20
    assert result["truncated"] is True
    assert result["duplicate_groups_truncated"] is True
    assert result["columns_truncated"] is False
    assert result["combinations_truncated"] is False


def test_selects_candidate_columns_beyond_original_column_order():
    data = {
        f"low_cardinality_{index}": ["A", "B"] * 10
        for index in range(12)
    }

    data["important_id"] = range(20)

    dataframe = pd.DataFrame(data)

    result = DuplicateService.analyze(dataframe)

    assert result["truncated"] is True
    assert result["evaluated_combinations"] > 0

    assert any(
        item["columns"] == ["important_id"]
        for item in result["candidate_keys"]
    )


def test_never_exceeds_maximum_evaluated_combinations():
    dataframe = pd.DataFrame({
        f"column_{index}": [
            f"value_{row}_{index}"
            for row in range(100)
        ]
        for index in range(12)
    })

    result = DuplicateService.analyze(dataframe)

    assert result["evaluated_combinations"] <= (
        DuplicateService.MAX_EVALUATED_COMBINATIONS
    )
    assert result["evaluated_combinations"] == 298
    assert result["combinations_truncated"] is False


def test_does_not_report_redundant_supersets_of_single_candidate_key():
    dataframe = pd.DataFrame({
        "id": [1, 2, 3],
        "name": ["Ana", "Luis", "Ana"],
        "age": [30, 25, 30],
    })

    result = DuplicateService.analyze(dataframe)

    candidate_columns = [
        item["columns"]
        for item in result["candidate_keys"]
    ]

    assert candidate_columns == [["id"]]


def test_does_not_report_redundant_supersets_of_composite_candidate_key():
    dataframe = pd.DataFrame({
        "customer_id": [1, 1, 2, 2],
        "order_id": [101, 102, 101, 102],
        "region": ["North", "South", "North", "South"],
    })

    result = DuplicateService.analyze(dataframe)

    candidate_columns = [
        item["columns"]
        for item in result["candidate_keys"]
    ]

    assert ["customer_id", "order_id"] in candidate_columns
    assert ["customer_id", "order_id", "region"] not in candidate_columns


def test_does_not_report_three_column_candidate_keys():
    dataframe = pd.DataFrame({
        "customer_id": [1, 1, 1, 2],
        "order_id": [101, 101, 102, 101],
        "region": ["North", "South", "North", "North"],
    })

    result = DuplicateService.analyze(dataframe)

    assert all(
        len(item["columns"]) <= 2
        for item in result["candidate_keys"]
    )


def test_serializes_non_finite_float_values_as_none():
    assert DuplicateService._serialize_value(float("nan")) is None
    assert DuplicateService._serialize_value(float("inf")) is None
    assert DuplicateService._serialize_value(float("-inf")) is None


def test_serializes_non_finite_numpy_values_as_none():
    assert DuplicateService._serialize_value(np.float64("nan")) is None
    assert DuplicateService._serialize_value(np.float64("inf")) is None
    assert DuplicateService._serialize_value(np.float64("-inf")) is None


def test_preserves_finite_numeric_values():
    assert DuplicateService._serialize_value(42.5) == 42.5
    assert DuplicateService._serialize_value(np.float64(42.5)) == 42.5
