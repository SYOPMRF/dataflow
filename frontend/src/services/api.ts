import type {
  AnalysisResult,
  ColumnAnalysis,
  DataQualityIssue,
  FileResponse,
  LoginResponse,
  UserResponse,
} from "../types/api";

const API_BASE_URL = "/api";

export class ApiError extends Error {
  public readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

type FileResponseDto = {
  id: number;
  original_name: string;
  stored_name: string;
  file_type: string;
  file_size: number;
  status: FileResponse["status"];
  created_at: string;
  processed_at: string | null;
};

function mapFileResponse(file: FileResponseDto): FileResponse {
  return {
    id: file.id,
    originalName: file.original_name,
    storedName: file.stored_name,
    fileType: file.file_type,
    fileSize: file.file_size,
    status: file.status,
    createdAt: file.created_at,
    processedAt: file.processed_at,
  };
}

type ColumnAnalysisDto = {
  name: string;
  data_type: string;
  null_count: number;
  unique_count: number;
  unique_percentage: number;
  duplicate_count: number;
  duplicated_unique_count: number;
  missing_percentage: number;
  is_completely_empty: boolean;
  min_value: unknown;
  max_value: unknown;
  mean_value: number | null;
  median_value: number | null;
  std_deviation: number | null;
  zero_count: number;
  negative_count: number;
};

type DataQualityIssueDto = {
  column_name: string;
  issue_type: string;
  severity: string;
  message: string;
};

type AnalysisResultDto = {
  row_count: number;
  column_count: number;
  missing_count: number;
  duplicate_count: number;
  columns: ColumnAnalysisDto[];
  quality_issues: DataQualityIssueDto[];
};

function mapColumnAnalysis(column: ColumnAnalysisDto): ColumnAnalysis {
  return {
    name: column.name,
    dataType: column.data_type,
    nullCount: column.null_count,
    uniqueCount: column.unique_count,
    uniquePercentage: column.unique_percentage,
    duplicateCount: column.duplicate_count,
    duplicatedUniqueCount: column.duplicated_unique_count,
    missingPercentage: column.missing_percentage,
    isCompletelyEmpty: column.is_completely_empty,
    minValue: column.min_value,
    maxValue: column.max_value,
    meanValue: column.mean_value,
    medianValue: column.median_value,
    stdDeviation: column.std_deviation,
    zeroCount: column.zero_count,
    negativeCount: column.negative_count,
  };
}

function mapDataQualityIssue(issue: DataQualityIssueDto): DataQualityIssue {
  return {
    columnName: issue.column_name,
    issueType: issue.issue_type,
    severity: issue.severity,
    message: issue.message,
  };
}

function mapAnalysisResult(result: AnalysisResultDto): AnalysisResult {
  return {
    rowCount: result.row_count,
    columnCount: result.column_count,
    missingCount: result.missing_count,
    duplicateCount: result.duplicate_count,
    columns: result.columns.map(mapColumnAnalysis),
    qualityIssues: result.quality_issues.map(mapDataQualityIssue),
  };
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, options);

  if (!response.ok) {
    let message = `Error HTTP ${response.status}`;

    try {
      const body: unknown = await response.json();

      if (typeof body === "object" && body !== null) {
        if ("error" in body && typeof body.error === "string") {
          message = body.error;
        } else if ("message" in body && typeof body.message === "string") {
          message = body.message;
        } else if ("detail" in body && typeof body.detail === "string") {
          message = body.detail;
        } else if (
          "errors" in body &&
          typeof body.errors === "object" &&
          body.errors !== null
        ) {
          const validationErrors = Object.entries(body.errors)
            .filter(
              (entry): entry is [string, string] =>
                typeof entry[1] === "string",
            )
            .map(([field, error]) => `${field}: ${error}`);

          if (validationErrors.length > 0) {
            message = validationErrors.join(". ");
          }
        }
      }
    } catch {
      // La respuesta puede no contener un cuerpo JSON.
    }

    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  async register(email: string, password: string): Promise<UserResponse> {
    return request<UserResponse>("/auth/register", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });
  },

  async login(email: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>("/auth/login", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, password }),
    });
  },

  async getFiles(token: string): Promise<FileResponse[]> {
    const files = await request<FileResponseDto[]>("/files", {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    return files.map(mapFileResponse);
  },

  async uploadFile(token: string, file: File): Promise<FileResponse> {
    const formData = new FormData();
    formData.append("file", file);

    const uploadedFile = await request<FileResponseDto>("/files/upload", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
      },
      body: formData,
    });

    return mapFileResponse(uploadedFile);
  },

  async getFile(token: string, fileId: number): Promise<FileResponse> {
    const file = await request<FileResponseDto>(`/files/${fileId}`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });

    return mapFileResponse(file);
  },

  async deleteFile(token: string, fileId: number): Promise<void> {
    return request<void>(`/files/${fileId}`, {
      method: "DELETE",
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
  },

  async processFile(token: string, fileId: number): Promise<AnalysisResult> {
    const result = await request<AnalysisResultDto>(
      `/files/${fileId}/process`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    );

    return mapAnalysisResult(result);
  },

  async getAnalysis(token: string, fileId: number): Promise<AnalysisResult> {
    const result = await request<AnalysisResultDto>(
      `/files/${fileId}/analysis`,
      {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      },
    );

    return mapAnalysisResult(result);
  },
};
