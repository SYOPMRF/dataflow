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
    it("recupera el análisis guardado sin volver a procesar el archivo", async () => {
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

      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledWith("/api/files/12/analysis", {
        headers: {
          Authorization: "Bearer token-prueba",
        },
      });

      expect(result).toEqual({
        rowCount: 2,
        columnCount: 1,
        missingCount: 0,
        duplicateCount: 0,
        columns: [],
        qualityIssues: [],
      });
    });
  });

  describe("processFile", () => {
    it("envía la petición y convierte las estadísticas y las incidencias de calidad", async () => {
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
      });
    });
  });
});
