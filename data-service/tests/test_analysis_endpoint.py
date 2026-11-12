from fastapi.testclient import TestClient

from app.main import app

import pytest


client = TestClient(app)


def test_analysis_endpoint_accepts_csv():
    csv_content = (
        "name,age,sales\n"
        "Alice,25,100\n"
        "Bob,30,200\n"
        "Charlie,,150\n"
        "Alice,25,100\n"
    )

    response = client.post(
        "/analysis",
        files={
            "file": (
                "test.csv",
                csv_content,
                "text/csv",
            )
        },
    )

    assert response.status_code == 200

    data = response.json()

    assert data["row_count"] == 4
    assert data["column_count"] == 3
    assert data["missing_count"] == 1
    assert data["duplicate_count"] == 1

    assert len(data["columns"]) == 3

    sales = next(
        column
        for column in data["columns"]
        if column["name"] == "sales"
    )

    assert sales["q1_value"] == 100.0
    assert sales["q3_value"] == 162.5
    assert sales["iqr_value"] == 62.5
    assert sales["p5_value"] == 100.0
    assert sales["p10_value"] == 100.0
    assert sales["p90_value"] == 185.0
    assert sales["p95_value"] == pytest.approx(192.5)
    assert sales["outlier_count"] == 0

    assert len(data["quality_issues"]) == 1

    issue = data["quality_issues"][0]

    assert issue["column_name"] == "age"
    assert issue["issue_type"] == "MISSING_VALUES"
    assert issue["severity"] == "MEDIUM"

    duplicate_analysis = data["duplicate_analysis"]

    assert duplicate_analysis["evaluated_combinations"] > 0
    assert isinstance(duplicate_analysis["candidate_keys"], list)
    assert isinstance(duplicate_analysis["duplicate_groups"], list)

    repeated_groups = duplicate_analysis["duplicate_groups"]

    assert repeated_groups

    assert all(
        len(group["columns"]) == 1
        for group in repeated_groups
    )

    name_group = next(
        group
        for group in repeated_groups
        if group["columns"] == ["name"]
    )

    assert name_group["duplicate_count"] == 1
    assert name_group["examples"][0]["frequency"] == 2


def test_analysis_endpoint_accepts_xlsx(tmp_path):
    import pandas as pd

    dataframe = pd.DataFrame({
        "product": ["Laptop", "Mouse", "Keyboard"],
        "price": [3500, 50, 120],
    })

    file_path = tmp_path / "products.xlsx"

    dataframe.to_excel(
        file_path,
        index=False,
    )

    response = client.post(
        "/analysis",
        files={
            "file": (
                "products.xlsx",
                file_path.read_bytes(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )

    assert response.status_code == 200

    data = response.json()

    assert data["row_count"] == 3
    assert data["column_count"] == 2
    assert data["missing_count"] == 0
    assert data["duplicate_count"] == 0

    assert data["columns"][0]["name"] == "product"
    assert data["columns"][1]["name"] == "price"

    assert data["quality_issues"] == []


def test_analysis_endpoint_rejects_unsupported_file_type():
    response = client.post(
        "/analysis",
        files={
            "file": (
                "test.txt",
                b"this is not a supported file",
                "text/plain",
            )
        },
    )

    assert response.status_code == 400

    data = response.json()

    assert data["detail"] == (
        "Unsupported file type. Only CSV and XLSX are supported."
    )


def test_analysis_endpoint_handles_invalid_file_content():
    response = client.post(
        "/analysis",
        files={
            "file": (
                "invalid.csv",
                b"this,is,not\nvalid,csv,data\xff",
                "text/csv",
            )
        },
    )

    assert response.status_code == 400

    data = response.json()

    assert "detail" in data


def test_analysis_endpoint_rejects_empty_file():
    response = client.post(
        "/analysis",
        files={
            "file": (
                "empty.csv",
                b"",
                "text/csv",
            )
        },
    )

    assert response.status_code == 400

    data = response.json()

    assert data["detail"] == "The uploaded file is empty."


def test_analysis_endpoint_rejects_missing_filename():
    response = client.post(
        "/analysis",
        files={
            "file": (
                "",
                b"name,age\nAlice,25",
                "text/csv",
            )
        },
    )

    assert response.status_code == 422


def test_analysis_endpoint_rejects_files_exceeding_10_mib():
    oversized_content = b"a" * (10 * 1024 * 1024 + 1)

    response = client.post(
        "/analysis",
        files={
            "file": (
                "oversized.csv",
                oversized_content,
                "text/csv",
            )
        },
    )

    assert response.status_code == 413
    assert response.json()["detail"] == (
        "File size exceeds the 10 MiB limit."
    )


def test_analysis_endpoint_accepts_file_at_exactly_10_mib(monkeypatch):
    from app.api.routes.analysis import AnalysisService

    expected_content = b"a" * (10 * 1024 * 1024)
    received = {}

    def fake_analyze_file(file_content, filename):
        received["file_content"] = file_content
        received["filename"] = filename
        return {
            "row_count": 0,
            "column_count": 0,
            "missing_count": 0,
            "duplicate_count": 0,
            "columns": [],
            "quality_issues": [],
        }

    monkeypatch.setattr(
        AnalysisService,
        "analyze_file",
        fake_analyze_file,
    )

    response = client.post(
        "/analysis",
        files={
            "file": (
                "exact-limit.csv",
                expected_content,
                "text/csv",
            )
        },
    )

    assert response.status_code == 200
    assert received["file_content"] == expected_content
    assert received["filename"] == "exact-limit.csv"
