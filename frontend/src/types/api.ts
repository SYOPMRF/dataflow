export interface UserResponse {
  id: number;
  email: string;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  user: UserResponse;
}

export interface FileResponse {
  id: number;
  originalName: string;
  storedName: string;
  fileType: string;
  fileSize: number;
  status: "UPLOADED" | "PROCESSING" | "COMPLETED" | "FAILED";
  createdAt: string;
  processedAt: string | null;
}

export interface ColumnAnalysis {
  name: string;
  dataType: string;
  nullCount: number;
  uniqueCount: number;
  uniquePercentage: number;
  duplicateCount: number;
  duplicatedUniqueCount: number;
  missingPercentage: number;
  isCompletelyEmpty: boolean;
  minValue: unknown;
  maxValue: unknown;
  meanValue: number | null;
  medianValue: number | null;
  stdDeviation: number | null;
  zeroCount: number;
  negativeCount: number;
}

export interface DataQualityIssue {
  columnName: string;
  issueType: string;
  severity: string;
  message: string;
}

export interface AnalysisResult {
  rowCount: number;
  columnCount: number;
  missingCount: number;
  duplicateCount: number;
  columns: ColumnAnalysis[];
  qualityIssues: DataQualityIssue[];
}
