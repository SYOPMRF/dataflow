import pandas as pd

from app.services.analysis.format_validation_service import (
    FormatValidationService,
)


def test_detects_invalid_emails():
    dataframe = pd.DataFrame({
        "email": [
            "alice@example.com",
            "invalid-email",
            "bob@example.org",
            "missing-domain@",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "email"
    assert issues[0]["issue_type"] == "INVALID_FORMAT"
    assert issues[0]["severity"] == "MEDIUM"
    assert issues[0]["affected_rows"] == 2


def test_ignores_missing_email_values():
    dataframe = pd.DataFrame({
        "email": [
            "alice@example.com",
            None,
            "",
            "bob@example.org",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []


def test_detects_invalid_urls():
    dataframe = pd.DataFrame({
        "website": [
            "https://example.com",
            "http://example.org/path",
            "ftp://example.com",
            "not-a-url",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "website"
    assert issues[0]["issue_type"] == "INVALID_FORMAT"
    assert issues[0]["affected_rows"] == 2


def test_normalizes_recognized_column_names():
    dataframe = pd.DataFrame({
        " Correo ": [
            "alice@example.com",
            "invalid-email",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == " Correo "
    assert issues[0]["affected_rows"] == 1


def test_ignores_ambiguous_columns():
    dataframe = pd.DataFrame({
        "description": [
            "alice@example.com",
            "not-an-email",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []


def test_ignores_completely_empty_email_columns():
    dataframe = pd.DataFrame({
        "email": [None, None, None],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []


def test_detects_invalid_dates():
    dataframe = pd.DataFrame({
        "delivery_date": [
            "2026-01-10",
            "2026-02-15",
            "not-a-date",
            "2026-03-20",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert len(issues) == 1
    assert issues[0]["column_name"] == "delivery_date"
    assert issues[0]["issue_type"] == "INVALID_FORMAT"
    assert issues[0]["severity"] == "MEDIUM"
    assert issues[0]["affected_rows"] == 1


def test_accepts_mixed_valid_date_formats():
    dataframe = pd.DataFrame({
        "fecha": [
            "2026-01-10",
            "2026/02/15",
            "March 20, 2026",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []


def test_ignores_missing_date_values():
    dataframe = pd.DataFrame({
        "created_at": [
            "2026-01-10",
            None,
            "",
            "2026-03-20",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []


def test_ignores_ambiguous_date_columns():
    dataframe = pd.DataFrame({
        "description": [
            "2026-01-10",
            "not-a-date",
        ],
    })

    issues = FormatValidationService.detect_issues(dataframe)

    assert issues == []
