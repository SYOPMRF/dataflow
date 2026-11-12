import pytest
from pydantic import ValidationError

from app.models.analysis import DataQualityIssue


def test_data_quality_issue_uses_default_values():
    issue = DataQualityIssue(
        column_name="age",
        issue_type="MISSING_VALUES",
        severity="MEDIUM",
        message="La columna contiene valores faltantes.",
    )

    assert issue.related_columns == []
    assert issue.affected_rows == 0


def test_data_quality_issue_accepts_related_columns_and_affected_rows():
    issue = DataQualityIssue(
        column_name="total_amount",
        issue_type="INCONSISTENT_TOTAL",
        severity="HIGH",
        message="El total no coincide con el importe calculado.",
        related_columns=["quantity", "unit_price", "discount_pct"],
        affected_rows=3,
    )

    assert issue.related_columns == [
        "quantity",
        "unit_price",
        "discount_pct",
    ]
    assert issue.affected_rows == 3


def test_data_quality_issue_rejects_negative_affected_rows():
    with pytest.raises(ValidationError):
        DataQualityIssue(
            column_name="age",
            issue_type="INVALID_VALUE",
            severity="MEDIUM",
            message="Valor inválido.",
            affected_rows=-1,
        )
