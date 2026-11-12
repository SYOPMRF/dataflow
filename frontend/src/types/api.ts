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
  q1Value: number | null;
  q3Value: number | null;
  iqrValue: number | null;
  p5Value: number | null;
  p10Value: number | null;
  p90Value: number | null;
  p95Value: number | null;
  outlierCount: number | null;
  zeroCount: number;
  negativeCount: number;
}

export interface DataQualityIssue {
  columnName: string;
  issueType: string;
  severity: string;
  message: string;
}

export interface CandidateKey {
  columns: string[];
  keyType: string;
  evaluatedRowCount: number;
  uniqueCount: number;
  uniquenessPercentage: number;
}

export interface DuplicateValueExample {
  values: Array<string | number | boolean | null>;
  frequency: number;
}

export interface DuplicateGroup {
  columns: string[];
  groupCount: number;
  duplicateCount: number;
  maxFrequency: number;
  examples: DuplicateValueExample[];
}

export interface DuplicateAnalysis {
  candidateKeys: CandidateKey[];
  duplicateGroups: DuplicateGroup[];
  evaluatedCombinations: number;
  columnsTruncated: boolean;
  combinationsTruncated: boolean;
  duplicateGroupsTruncated: boolean;
  truncated: boolean;
}

export interface DistributionBin {
  lowerBound: number;
  upperBound: number;
  count: number;
  percentage: number;
}

export interface DistributionCategory {
  value: unknown;
  count: number;
  percentage: number;
}

export interface ColumnDistribution {
  column: string;
  distributionType: string;
  validCount: number;
  nullCount: number;
  binCount: number | null;
  bins: DistributionBin[];
  categoryCount: number | null;
  categories: DistributionCategory[];
  categoriesTruncated: boolean;
}

export interface DistributionAnalysis {
  columns: ColumnDistribution[];
}

export interface CorrelationAnalysis {
  columns: string[];
  pearson: Array<Array<number | null>>;
  spearman: Array<Array<number | null>>;
  observationCounts: number[][];
  truncated: boolean;
}

export interface AnalysisResult {
  rowCount: number;
  columnCount: number;
  missingCount: number;
  duplicateCount: number;
  columns: ColumnAnalysis[];
  qualityIssues: DataQualityIssue[];
  duplicateAnalysis: DuplicateAnalysis;
  distributionAnalysis: DistributionAnalysis;
  correlationAnalysis: CorrelationAnalysis;
}