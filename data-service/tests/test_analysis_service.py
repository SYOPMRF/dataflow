from app.models.analysis import AnalysisResult
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


def test_analyze_dataframe_calculates_percentiles_and_outliers():
    dataframe = pd.DataFrame({
        "sales": [10, 12, 14, 16, 18, 20, 22, 24, 100],
    })

    result = AnalysisService.analyze_dataframe(dataframe)
    column = result["columns"][0]

    assert column["q1_value"] == pytest.approx(14.0)
    assert column["q3_value"] == pytest.approx(22.0)
    assert column["iqr_value"] == pytest.approx(8.0)

    assert column["p5_value"] == pytest.approx(10.8)
    assert column["p10_value"] == pytest.approx(11.6)
    assert column["p90_value"] == pytest.approx(39.2)
    assert column["p95_value"] == pytest.approx(69.6)

    assert column["outlier_count"] == 1


def test_analyze_dataframe_ignores_non_finite_numeric_values():
    dataframe = pd.DataFrame({
        "values": [1.0, 2.0, float("inf"), float("-inf"), float("nan")],
    })

    result = AnalysisService.analyze_dataframe(dataframe)
    column = result["columns"][0]

    assert column["min_value"] == 1.0
    assert column["max_value"] == 2.0
    assert column["mean_value"] == pytest.approx(1.5)
    assert column["q1_value"] == pytest.approx(1.25)
    assert column["q3_value"] == pytest.approx(1.75)
    assert column["outlier_count"] == 0


def test_analyze_dataframe_handles_empty_numeric_and_text_columns():
    dataframe = pd.DataFrame({
        "empty_numeric": pd.Series([None, None], dtype="float64"),
        "name": ["Alice", "Bob"],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    numeric = result["columns"][0]
    text = result["columns"][1]

    assert numeric["q1_value"] is None
    assert numeric["q3_value"] is None
    assert numeric["iqr_value"] is None
    assert numeric["outlier_count"] == 0

    assert text["q1_value"] is None
    assert text["q3_value"] is None
    assert text["iqr_value"] is None
    assert text["outlier_count"] is None


def test_duplicate_analysis_uses_empty_defaults():
    from app.models.analysis import DuplicateAnalysis

    result = DuplicateAnalysis()

    assert result.candidate_keys == []
    assert result.duplicate_groups == []
    assert result.evaluated_combinations == 0
    assert result.truncated is False


def test_analysis_result_accepts_duplicate_analysis():
    from app.models.analysis import AnalysisResult

    result = AnalysisResult(
        row_count=100,
        column_count=3,
        missing_count=0,
        duplicate_count=2,
        columns=[],
        quality_issues=[],
        duplicate_analysis={
            "candidate_keys": [
                {
                    "columns": ["customer_id", "order_id"],
                    "key_type": "COMPOSITE",
                    "evaluated_row_count": 100,
                    "unique_count": 100,
                    "uniqueness_percentage": 100.0,
                }
            ],
            "duplicate_groups": [
                {
                    "columns": ["product", "region"],
                    "group_count": 2,
                    "duplicate_count": 3,
                    "max_frequency": 3,
                    "examples": [
                        {
                            "values": ["Laptop", "North"],
                            "frequency": 3,
                        },
                        {
                            "values": ["Phone", "South"],
                            "frequency": 2,
                        },
                    ],
                }
            ],
            "evaluated_combinations": 12,
            "truncated": False,
        },
    )

    assert len(result.duplicate_analysis.candidate_keys) == 1
    assert result.duplicate_analysis.candidate_keys[0].key_type == "COMPOSITE"
    assert result.duplicate_analysis.duplicate_groups[0].max_frequency == 3
    assert result.duplicate_analysis.duplicate_groups[0].examples[0].frequency == 3


def test_analyze_dataframe_detects_z_score_outliers():
    dataframe = pd.DataFrame({
        "values": [10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 1000],
    })

    result = AnalysisService.analyze_dataframe(dataframe)
    column = result["columns"][0]

    assert column["z_score_outlier_count"] == 1


def test_analyze_dataframe_ignores_non_finite_values_for_z_score():
    dataframe = pd.DataFrame({
        "values": [1.0, 2.0, float("inf"), float("-inf"), float("nan")],
    })

    result = AnalysisService.analyze_dataframe(dataframe)
    column = result["columns"][0]

    assert column["z_score_outlier_count"] == 0


def test_analyze_dataframe_returns_zero_z_score_outliers_for_constant_values():
    dataframe = pd.DataFrame({
        "values": [5.0, 5.0, 5.0, 5.0],
    })

    result = AnalysisService.analyze_dataframe(dataframe)
    column = result["columns"][0]

    assert column["z_score_outlier_count"] == 0


def test_analyze_dataframe_handles_z_score_for_empty_and_text_columns():
    dataframe = pd.DataFrame({
        "empty_numeric": pd.Series([None, None], dtype="float64"),
        "name": ["Alice", "Bob"],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    assert result["columns"][0]["z_score_outlier_count"] == 0
    assert result["columns"][1]["z_score_outlier_count"] is None


def test_analyze_dataframe_includes_semantic_issues_in_analysis_result():
    dataframe = pd.DataFrame({
        "order_status": ["Delivered", "Pending", "Delivered"],
        "delivery_date": [None, None, "2026-01-10"],
        "quantity": [2, 3, 1],
        "unit_price": [50.0, 20.0, 10.0],
        "discount_pct": [10.0, 105.0, 0.0],
        "total_amount": [90.0, 50.0, 10.0],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    validated_result = AnalysisResult.model_validate(result)

    issues = {
        issue.issue_type: issue
        for issue in validated_result.quality_issues
    }

    assert "DELIVERED_WITHOUT_DELIVERY_DATE" in issues
    assert "INVALID_DISCOUNT" in issues
    assert issues["DELIVERED_WITHOUT_DELIVERY_DATE"].affected_rows == 1
    assert issues["INVALID_DISCOUNT"].affected_rows == 1

    assert "INCONSISTENT_ORDER_TOTAL" not in issues


def test_analyze_dataframe_returns_zero_std_deviation_for_single_value():
    dataframe = pd.DataFrame({
        "value": [42],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    column = result["columns"][0]

    assert column["mean_value"] == 42.0
    assert column["median_value"] == 42.0
    assert column["std_deviation"] == 0.0
    assert column["outlier_count"] == 0
    assert column["z_score_outlier_count"] == 0


def test_analyze_dataframe_includes_format_issues_in_analysis_result():
    dataframe = pd.DataFrame({
        "email": [
            "alice@example.com",
            "invalid-email",
            "bob@example.org",
            "missing-domain@",
        ],
    })

    result = AnalysisService.analyze_dataframe(dataframe)

    validated_result = AnalysisResult.model_validate(result)

    format_issues = [
        issue
        for issue in validated_result.quality_issues
        if issue.issue_type == "INVALID_FORMAT"
    ]

    assert len(format_issues) == 1
    assert format_issues[0].column_name == "email"
    assert format_issues[0].severity == "MEDIUM"
    assert format_issues[0].affected_rows == 2
    assert format_issues[0].related_columns == ["email"]
