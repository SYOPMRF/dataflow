import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "./api";

function mockResponse(body: unknown, status = 200, ok = true) {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(body),
  };
}

describe("api", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  describe("login", () => {
    it("envía las credenciales y devuelve la sesión", async () => {
      const session = {
        token: "token-de-prueba",
        user: {
          id: 1,
          email: "usuario@ejemplo.com",
          createdAt: "2026-10-16T12:00:00",
        },
      };

      const fetchMock = vi.fn().mockResolvedValue(mockResponse(session));

      vi.stubGlobal("fetch", fetchMock);

      const result = await api.login("usuario@ejemplo.com", "claveSegura123");

      expect(fetchMock).toHaveBeenCalledWith("/api/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: "usuario@ejemplo.com",
          password: "claveSegura123",
        }),
      });

      expect(result).toEqual(session);
    });
  });

  describe("register", () => {
    it("envía los datos de registro y devuelve el usuario", async () => {
      const user = {
        id: 2,
        email: "nuevo@ejemplo.com",
        createdAt: "2026-10-16T12:00:00",
      };

      const fetchMock = vi.fn().mockResolvedValue(mockResponse(user));

      vi.stubGlobal("fetch", fetchMock);

      const result = await api.register("nuevo@ejemplo.com", "claveSegura123");

      expect(fetchMock).toHaveBeenCalledWith("/api/auth/register", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          email: "nuevo@ejemplo.com",
          password: "claveSegura123",
        }),
      });

      expect(result).toEqual(user);
    });
  });

  describe("getFiles", () => {
    it("envía el token y convierte los campos de snake_case a camelCase", async () => {
      const fetchMock = vi.fn().mockResolvedValue(
        mockResponse([
          {
            id: 7,
            original_name: "ventas.csv",
            stored_name: "uuid.csv",
            file_type: "CSV",
            file_size: 1024,
            status: "COMPLETED",
            created_at: "2026-10-16T10:00:00",
            processed_at: "2026-10-16T10:05:00",
          },
        ]),
      );

      vi.stubGlobal("fetch", fetchMock);

      const files = await api.getFiles("token-prueba");

      expect(fetchMock).toHaveBeenCalledWith("/api/files", {
        headers: {
          Authorization: "Bearer token-prueba",
        },
      });

      expect(files).toEqual([
        {
          id: 7,
          originalName: "ventas.csv",
          storedName: "uuid.csv",
          fileType: "CSV",
          fileSize: 1024,
          status: "COMPLETED",
          createdAt: "2026-10-16T10:00:00",
          processedAt: "2026-10-16T10:05:00",
        },
      ]);
    });
  });

  describe("uploadFile", () => {
    it("envía el archivo como multipart sin establecer Content-Type manualmente", async () => {
      const fetchMock = vi.fn().mockResolvedValue(
        mockResponse({
          id: 8,
          original_name: "datos.csv",
          stored_name: "uuid.csv",
          file_type: "CSV",
          file_size: 256,
          status: "UPLOADED",
          created_at: "2026-10-16T10:00:00",
          processed_at: null,
        }),
      );

      vi.stubGlobal("fetch", fetchMock);

      const file = new File(["nombre,edad\nAna,25"], "datos.csv", {
        type: "text/csv",
      });

      const result = await api.uploadFile("token-prueba", file);

      expect(fetchMock).toHaveBeenCalledTimes(1);

      const [url, options] = fetchMock.mock.calls[0] as [string, RequestInit];

      expect(url).toBe("/api/files/upload");
      expect(options.method).toBe("POST");
      expect(new Headers(options.headers).get("Authorization")).toBe(
        "Bearer token-prueba",
      );
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      expect(options.body).toBeInstanceOf(FormData);
      expect((options.body as FormData).get("file")).toBe(file);

      expect(result.originalName).toBe("datos.csv");
      expect(result.processedAt).toBeNull();
    });
  });

  describe("deleteFile", () => {
    it("acepta una respuesta 204 sin cuerpo", async () => {
      const fetchMock = vi.fn().mockResolvedValue(mockResponse(null, 204));

      vi.stubGlobal("fetch", fetchMock);

      await expect(api.deleteFile("token-prueba", 8)).resolves.toBeUndefined();

      expect(fetchMock).toHaveBeenCalledWith("/api/files/8", {
        method: "DELETE",
        headers: {
          Authorization: "Bearer token-prueba",
        },
      });
    });
  });

  describe("errores HTTP", () => {
    it("convierte un error 401 en ApiError y conserva el estado", async () => {
      const fetchMock = vi
        .fn()
        .mockResolvedValue(
          mockResponse({ message: "Token inválido" }, 401, false),
        );

      vi.stubGlobal("fetch", fetchMock);

      await expect(api.getFiles("token-invalido")).rejects.toMatchObject({
        name: "ApiError",
        message: "Token inválido",
        status: 401,
      });
    });

    it("usa el estado HTTP cuando la respuesta de error no contiene JSON válido", async () => {
      const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
        json: vi.fn().mockRejectedValue(new Error("JSON inválido")),
      });

      vi.stubGlobal("fetch", fetchMock);

      try {
        await api.getFiles("token-prueba");
        throw new Error("Se esperaba que la petición fallara");
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({
          message: "Error HTTP 500",
          status: 500,
        });
      }
    });
  });

describe("getAnalysis", () => {
  it("recupera el análisis guardado y convierte distribución y correlación", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockResponse({
        row_count: 20,
        column_count: 2,
        missing_count: 1,
        duplicate_count: 2,
        columns: [],
        quality_issues: [],
        distribution_analysis: {
          columns: [
            {
              column: "edad",
              distribution_type: "numeric",
              valid_count: 19,
              null_count: 1,
              bin_count: 2,
              bins: [
                {
                  lower_bound: 20,
                  upper_bound: 30,
                  count: 10,
                  percentage: 52.63,
                },
                {
                  lower_bound: 30,
                  upper_bound: 40,
                  count: 9,
                  percentage: 47.37,
                },
              ],
              category_count: null,
              categories: [],
              categories_truncated: false,
            },
            {
              column: "ciudad",
              distribution_type: "categorical",
              valid_count: 20,
              null_count: 0,
              bin_count: null,
              bins: [],
              category_count: 2,
              categories: [
                { value: "Bogotá", count: 12, percentage: 60 },
                { value: "Cali", count: 8, percentage: 40 },
              ],
              categories_truncated: false,
            },
          ],
        },
        correlation_analysis: {
          columns: ["edad", "salario"],
          pearson: [
            [1, 0.85],
            [0.85, 1],
          ],
          spearman: [
            [1, 0.8],
            [0.8, 1],
          ],
          observation_counts: [
            [20, 18],
            [18, 19],
          ],
          truncated: false,
        },
      }),
    );

    vi.stubGlobal("fetch", fetchMock);

    const result = await api.getAnalysis("token-prueba", 12);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/files/12/analysis", {
      headers: {
        Authorization: "Bearer token-prueba",
      },
    });

    expect(result.distributionAnalysis).toEqual({
      columns: [
        {
          column: "edad",
          distributionType: "numeric",
          validCount: 19,
          nullCount: 1,
          binCount: 2,
          bins: [
            {
              lowerBound: 20,
              upperBound: 30,
              count: 10,
              percentage: 52.63,
            },
            {
              lowerBound: 30,
              upperBound: 40,
              count: 9,
              percentage: 47.37,
            },
          ],
          categoryCount: null,
          categories: [],
          categoriesTruncated: false,
        },
        {
          column: "ciudad",
          distributionType: "categorical",
          validCount: 20,
          nullCount: 0,
          binCount: null,
          bins: [],
          categoryCount: 2,
          categories: [
            { value: "Bogotá", count: 12, percentage: 60 },
            { value: "Cali", count: 8, percentage: 40 },
          ],
          categoriesTruncated: false,
        },
      ],
    });

    expect(result.correlationAnalysis).toEqual({
      columns: ["edad", "salario"],
      pearson: [
        [1, 0.85],
        [0.85, 1],
      ],
      spearman: [
        [1, 0.8],
        [0.8, 1],
      ],
      observationCounts: [
        [20, 18],
        [18, 19],
      ],
      truncated: false,
    });
  });

  it("usa estructuras vacías cuando recupera un análisis antiguo sin bloques avanzados", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockResponse({
        row_count: 2,
        column_count: 1,
        missing_count: 0,
        duplicate_count: 0,
        columns: [],
        quality_issues: [],
      }),
    );

    vi.stubGlobal("fetch", fetchMock);

    const result = await api.getAnalysis("token-prueba", 12);

    expect(result.distributionAnalysis).toEqual({ columns: [] });
    expect(result.correlationAnalysis).toEqual({
      columns: [],
      pearson: [],
      spearman: [],
      observationCounts: [],
      truncated: false,
    });
    expect(result.duplicateAnalysis).toEqual({
      candidateKeys: [],
      duplicateGroups: [],
      evaluatedCombinations: 0,
      columnsTruncated: false,
      combinationsTruncated: false,
      duplicateGroupsTruncated: false,
      truncated: false,
    });
  });
});

describe("processFile", () => {
  it("envía la petición y convierte las estadísticas avanzadas, duplicados e incidencias de calidad", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      mockResponse({
        row_count: 3,
        column_count: 1,
        missing_count: 1,
        duplicate_count: 0,
        columns: [
          {
            name: "edad",
            data_type: "integer",
            null_count: 1,
            unique_count: 2,
            unique_percentage: 66.67,
            duplicate_count: 0,
            duplicated_unique_count: 0,
            missing_percentage: 33.33,
            is_completely_empty: false,
            min_value: 20,
            max_value: 30,
            mean_value: 25,
            median_value: 25,
            std_deviation: 5,
            q1_value: 22,
            q3_value: 28,
            iqr_value: 6,
            p5_value: 20.5,
            p10_value: 21,
            p90_value: 29,
            p95_value: 29.5,
            outlier_count: 1,
            zero_count: 0,
            negative_count: 0,
          },
        ],
        quality_issues: [
          {
            column_name: "edad",
            issue_type: "HIGH_MISSING_VALUES",
            severity: "WARNING",
            message: "La columna tiene valores faltantes.",
          },
        ],
        duplicate_analysis: {
          candidate_keys: [
            {
              columns: ["edad"],
              key_type: "single",
              evaluated_row_count: 3,
              unique_count: 3,
              uniqueness_percentage: 100,
            },
          ],
          duplicate_groups: [
            {
              columns: ["edad"],
              group_count: 1,
              duplicate_count: 1,
              max_frequency: 2,
              examples: [
                {
                  values: [25],
                  frequency: 2,
                },
              ],
            },
          ],
          evaluated_combinations: 1,
          columns_truncated: true,
          combinations_truncated: false,
          duplicate_groups_truncated: true,
          truncated: true,
        },
      }),
    );

    vi.stubGlobal("fetch", fetchMock);

    const result = await api.processFile("token-prueba", 12);

    expect(fetchMock).toHaveBeenCalledWith("/api/files/12/process", {
      method: "POST",
      headers: {
        Authorization: "Bearer token-prueba",
      },
    });

    expect(result).toEqual({
      rowCount: 3,
      columnCount: 1,
      missingCount: 1,
      duplicateCount: 0,
      columns: [
        {
          name: "edad",
          dataType: "integer",
          nullCount: 1,
          uniqueCount: 2,
          uniquePercentage: 66.67,
          duplicateCount: 0,
          duplicatedUniqueCount: 0,
          missingPercentage: 33.33,
          isCompletelyEmpty: false,
          minValue: 20,
          maxValue: 30,
          meanValue: 25,
          medianValue: 25,
          stdDeviation: 5,
          q1Value: 22,
          q3Value: 28,
          iqrValue: 6,
          p5Value: 20.5,
          p10Value: 21,
          p90Value: 29,
          p95Value: 29.5,
          outlierCount: 1,
          zeroCount: 0,
          negativeCount: 0,
        },
      ],
      qualityIssues: [
        {
          columnName: "edad",
          issueType: "HIGH_MISSING_VALUES",
          severity: "WARNING",
          message: "La columna tiene valores faltantes.",
        },
      ],
      duplicateAnalysis: {
        candidateKeys: [
          {
            columns: ["edad"],
            keyType: "single",
            evaluatedRowCount: 3,
            uniqueCount: 3,
            uniquenessPercentage: 100,
          },
        ],
        duplicateGroups: [
          {
            columns: ["edad"],
            groupCount: 1,
            duplicateCount: 1,
            maxFrequency: 2,
            examples: [
              {
                values: [25],
                frequency: 2,
              },
            ],
          },
        ],
        evaluatedCombinations: 1,
        columnsTruncated: true,
        combinationsTruncated: false,
        duplicateGroupsTruncated: true,
        truncated: true,
      },
      distributionAnalysis: {
        columns: [],
      },
      correlationAnalysis: {
        columns: [],
        pearson: [],
        spearman: [],
        observationCounts: [],
        truncated: false,
      },
    });
  });
});
});
