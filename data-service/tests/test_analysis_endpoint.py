from fastapi.testclient import TestClient

from app.main import app


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

    assert len(data["quality_issues"]) == 1

    issue = data["quality_issues"][0]

    assert issue["column_name"] == "age"
    assert issue["issue_type"] == "MISSING_VALUES"
    assert issue["severity"] == "MEDIUM"

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