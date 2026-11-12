import pandas as pd

from app.services.analysis.semantic_validation_service import (
    SemanticValidationService,
)


def test_detects_delivered_orders_without_delivery_date():
    dataframe = pd.DataFrame({
        "order_status": ["Delivered", "Pending", "Delivered"],
        "delivery_date": [None, None, "2026-01-10"],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "delivery_date"
    assert issues[0]["issue_type"] == "DELIVERED_WITHOUT_DELIVERY_DATE"
    assert issues[0]["severity"] == "HIGH"
    assert issues[0]["related_columns"] == [
        "order_status",
        "delivery_date",
    ]
    assert issues[0]["affected_rows"] == 1


def test_detects_inconsistent_order_totals():
    dataframe = pd.DataFrame({
        "quantity": [2, 3],
        "unit_price": [50.0, 20.0],
        "discount_pct": [10.0, 0.0],
        "total_amount": [90.0, 50.0],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "total_amount"
    assert issues[0]["issue_type"] == "INCONSISTENT_ORDER_TOTAL"
    assert issues[0]["severity"] == "MEDIUM"
    assert issues[0]["related_columns"] == [
        "quantity",
        "unit_price",
        "discount_pct",
        "total_amount",
    ]
    assert issues[0]["affected_rows"] == 1


def test_detects_invalid_discounts():
    dataframe = pd.DataFrame({
        "discount_pct": [-5.0, 0.0, 50.0, 105.0],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "discount_pct"
    assert issues[0]["issue_type"] == "INVALID_DISCOUNT"
    assert issues[0]["severity"] == "HIGH"
    assert issues[0]["related_columns"] == ["discount_pct"]
    assert issues[0]["affected_rows"] == 2


def test_ignores_rules_when_required_columns_are_missing():
    dataframe = pd.DataFrame({
        "quantity": [2],
        "unit_price": [50.0],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert issues == []


def test_ignores_incomplete_rows_when_validating_totals():
    dataframe = pd.DataFrame({
        "quantity": [2, None],
        "unit_price": [50.0, 20.0],
        "discount_pct": [10.0, 0.0],
        "total_amount": [90.0, 20.0],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert issues == []


def test_accepts_totals_within_currency_tolerance():
    dataframe = pd.DataFrame({
        "quantity": [2],
        "unit_price": [50.0],
        "discount_pct": [10.0],
        "total_amount": [90.01],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert issues == []


def test_invalid_discount_does_not_generate_inconsistent_total():
    dataframe = pd.DataFrame({
        "quantity": [2],
        "unit_price": [50.0],
        "discount_pct": [105.0],
        "total_amount": [0.0],
    })

    issues = SemanticValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "INVALID_DISCOUNT"
    assert issues[0]["affected_rows"] == 1
