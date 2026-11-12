from app.services.analysis.quality_service import QualityService


def test_detects_completely_empty_column():
    columns = [
        {
            "name": "email",
            "null_count": 10,
            "missing_percentage": 100.0,
            "is_completely_empty": True,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "COMPLETELY_EMPTY"
    assert issues[0]["severity"] == "HIGH"
    assert issues[0]["affected_rows"] == 10
    assert issues[0]["related_columns"] == []


def test_detects_high_missing_values():
    columns = [
        {
            "name": "age",
            "null_count": 6,
            "missing_percentage": 60.0,
            "is_completely_empty": False,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "HIGH_MISSING_VALUES"
    assert issues[0]["severity"] == "HIGH"
    assert issues[0]["affected_rows"] == 6
    assert issues[0]["related_columns"] == []


def test_detects_missing_values():
    columns = [
        {
            "name": "age",
            "null_count": 2,
            "missing_percentage": 25.0,
            "is_completely_empty": False,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "MISSING_VALUES"
    assert issues[0]["severity"] == "MEDIUM"
    assert issues[0]["affected_rows"] == 2
    assert issues[0]["related_columns"] == []


def test_detects_negative_values():
    columns = [
        {
            "name": "sales",
            "null_count": 0,
            "missing_percentage": 0.0,
            "is_completely_empty": False,
            "negative_count": 3,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "NEGATIVE_VALUES"
    assert issues[0]["severity"] == "MEDIUM"
    assert issues[0]["affected_rows"] == 3
    assert issues[0]["related_columns"] == []


def test_detects_multiple_issues():
    columns = [
        {
            "name": "sales",
            "null_count": 2,
            "missing_percentage": 25.0,
            "is_completely_empty": False,
            "negative_count": 2,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 2

    issue_types = {issue["issue_type"] for issue in issues}
    assert "MISSING_VALUES" in issue_types
    assert "NEGATIVE_VALUES" in issue_types

    assert all(isinstance(issue["affected_rows"], int) for issue in issues)
    assert all(issue["affected_rows"] >= 0 for issue in issues)
    assert all(issue["related_columns"] == [] for issue in issues)
