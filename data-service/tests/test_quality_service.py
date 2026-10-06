from app.services.analysis.quality_service import QualityService


def test_detects_completely_empty_column():
    columns = [
        {
            "name": "email",
            "missing_percentage": 100.0,
            "is_completely_empty": True,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "COMPLETELY_EMPTY"
    assert issues[0]["severity"] == "HIGH"


def test_detects_high_missing_values():
    columns = [
        {
            "name": "age",
            "missing_percentage": 60.0,
            "is_completely_empty": False,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "HIGH_MISSING_VALUES"
    assert issues[0]["severity"] == "HIGH"


def test_detects_missing_values():
    columns = [
        {
            "name": "age",
            "missing_percentage": 25.0,
            "is_completely_empty": False,
            "negative_count": 0,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "MISSING_VALUES"
    assert issues[0]["severity"] == "MEDIUM"


def test_detects_negative_values():
    columns = [
        {
            "name": "sales",
            "missing_percentage": 0.0,
            "is_completely_empty": False,
            "negative_count": 3,
        }
    ]

    issues = QualityService.detect_issues(columns)

    assert len(issues) == 1
    assert issues[0]["issue_type"] == "NEGATIVE_VALUES"
    assert issues[0]["severity"] == "MEDIUM"


def test_detects_multiple_issues():
    columns = [
        {
            "name": "sales",
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
