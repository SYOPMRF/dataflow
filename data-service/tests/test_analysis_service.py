import pandas as pd
import pytest

from app.services.analysis.analysis_service import AnalysisService


def test_analyze_dataframe_returns_basic_metrics():
    dataframe = pd.DataFrame({
        "name": ["Alice", "Bob", "Charlie", "Alice"],
        "age": [25, 30, None, 25],
        "sales": [100, 200, 150, 100],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    assert result["row_count"] == 4
    assert result["column_count"] == 3
    assert result["missing_count"] == 1
    assert result["duplicate_count"] == 1

    name = result["columns"][0]

    assert name["duplicate_count"] == 1

    assert name["duplicated_unique_count"] == 1

    age = result["columns"][1]

    assert age["null_count"] == 1
    assert age["missing_percentage"] == 25.0
    assert age["unique_count"] == 2
    assert age["unique_percentage"] == 50.0


def test_analyze_dataframe_calculates_numeric_statistics():
    dataframe = pd.DataFrame({
        "age": [25, 30, None, 25],
        "sales": [100, 200, 150, 100],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    age = result["columns"][0]
    sales = result["columns"][1]

    assert age["min_value"] == 25.0
    assert age["max_value"] == 30.0
    assert age["mean_value"] == pytest.approx(26.6666666667)

    assert age["median_value"] == 25.0

    assert sales["min_value"] == 100.0
    assert sales["max_value"] == 200.0
    assert sales["mean_value"] == 137.5
    assert sales["median_value"] == 125.0
    assert age["std_deviation"] == pytest.approx(2.8867513459)
    assert sales["std_deviation"] == pytest.approx(47.871355)


def test_analyze_dataframe_does_not_calculate_statistics_for_text_columns():
    dataframe = pd.DataFrame({
        "name": ["Alice", "Bob", "Charlie"]
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    column = result["columns"][0]

    assert column["name"] == "name"
    assert column["data_type"] == "text"
    assert column["null_count"] == 0
    assert column["missing_percentage"] == 0.0
    assert column["unique_count"] == 3
    assert column["duplicated_unique_count"] == 0
    assert column["unique_percentage"] == 100.0
    assert column["min_value"] is None
    assert column["max_value"] is None
    assert column["mean_value"] is None
    assert column["median_value"] is None
    assert column["std_deviation"] is None
    assert column["zero_count"] == 0
    assert column["negative_count"] == 0

def test_load_dataframe_rejects_unsupported_file_type():
    with pytest.raises(ValueError, match="Unsupported file type"):
        AnalysisService.load_dataframe(
            b"some content",
            "data.txt"
        )


def test_load_dataframe_supports_xlsx(tmp_path):
    dataframe = pd.DataFrame({
        "name": ["Alice", "Bob"],
        "sales": [100, 200],
    })

    file_path = tmp_path / "test_data.xlsx"
    dataframe.to_excel(file_path, index=False)

    file_content = file_path.read_bytes()

    loaded_dataframe = AnalysisService.load_dataframe(
        file_content,
        "test_data.xlsx"
    )

    assert list(loaded_dataframe.columns) == ["name", "sales"]
    assert len(loaded_dataframe) == 2
    assert loaded_dataframe["sales"].tolist() == [100, 200]

def test_analyze_dataframe_detects_completely_empty_columns():
    dataframe = pd.DataFrame({
        "name": ["Alice", "Bob", "Charlie"],
        "email": [None, None, None],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    name_column = result["columns"][0]
    email_column = result["columns"][1]

    assert name_column["is_completely_empty"] is False
    assert email_column["null_count"] == 3
    assert email_column["missing_percentage"] == 100.0
    assert email_column["is_completely_empty"] is True

def test_get_data_type_classifies_common_data_types():
    dataframe = pd.DataFrame({
        "integer": pd.Series([1, 2, 3], dtype="int64"),
        "decimal": pd.Series([1.5, 2.5, 3.5], dtype="float64"),
        "text": ["Alice", "Bob", "Charlie"],
        "boolean": [True, False, True],
    })

    assert AnalysisService.get_data_type(dataframe["integer"]) == "integer"
    assert AnalysisService.get_data_type(dataframe["decimal"]) == "decimal"
    assert AnalysisService.get_data_type(dataframe["text"]) == "text"
    assert AnalysisService.get_data_type(dataframe["boolean"]) == "boolean"

def test_get_data_type_classifies_datetime():
    series = pd.Series(
        pd.to_datetime([
            "2026-01-01",
            "2026-01-02",
            "2026-01-03",
        ])
    )

    assert AnalysisService.get_data_type(series) == "datetime"

def test_get_data_type_detects_date_columns():
    series = pd.Series([
        "2026-01-01",
        "2026-02-15",
        "2026-03-20",
    ])

    assert AnalysisService.get_data_type(series) == "date"

def test_get_data_type_does_not_misclassify_text_as_date():
    series = pd.Series([
        "Alice",
        "Bob",
        "Charlie",
    ])

    assert AnalysisService.get_data_type(series) == "text"

def test_analyze_dataframe_counts_zero_and_negative_values():
    dataframe = pd.DataFrame({
        "values": [100, 0, -20, 50, 0, -10],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    column = result["columns"][0]

    assert column["zero_count"] == 2
    assert column["negative_count"] == 2

def test_analyze_dataframe_counts_duplicated_unique_values():
    dataframe = pd.DataFrame({
        "name": [
            "Alice",
            "Alice",
            "Bob",
            "Charlie",
            "Charlie",
            "David",
        ]
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    column = result["columns"][0]

    assert column["duplicate_count"] == 2
    assert column["duplicated_unique_count"] == 2