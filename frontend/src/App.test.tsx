import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";
import { ApiError, api } from "./services/api";

vi.mock("./services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./services/api")>();

  return {
    ...actual,
    api: {
      ...actual.api,
      login: vi.fn(),
      getFiles: vi.fn(),
      uploadFile: vi.fn(),
      processFile: vi.fn(),
      getAnalysis: vi.fn(),
    },
  };
});

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("informa cuando la subida funciona pero falla la actualización de archivos", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles)
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(new Error("No fue posible actualizar la lista."));

    vi.mocked(api.uploadFile).mockResolvedValue({
      id: 10,
      originalName: "ventas.csv",
      storedName: "test-uuid.csv",
      fileType: "text/csv",
      fileSize: 100,
      status: "UPLOADED",
      createdAt: "2026-01-01T00:00:00Z",
      processedAt: null,
    });

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledTimes(1);
    });

    const fileInput = document.querySelector(
      'input[type="file"]',
    ) as HTMLInputElement;

    const file = new File(["nombre,valor\nProducto,10"], "ventas.csv", {
      type: "text/csv",
    });

    fireEvent.change(fileInput, {
      target: { files: [file] },
    });

    fireEvent.click(screen.getByRole("button", { name: "Subir archivo" }));

    await waitFor(() => {
      expect(api.uploadFile).toHaveBeenCalledWith("test-token", file);
      expect(api.getFiles).toHaveBeenCalledTimes(2);
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "El archivo se cargó correctamente, pero no fue posible actualizar la lista: No fue posible actualizar la lista.",
    );

    expect(
      screen.getByRole("button", { name: /Intentar de nuevo/i }),
    ).toBeInTheDocument();
  });

  it("regresa al inicio de sesión si la carga de archivos devuelve 401", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockRejectedValue(
      new ApiError("No autorizado", 401),
    );

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Tu sesión ha expirado. Inicia sesión nuevamente.",
    );

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();
  });

  it("conserva los resultados si falla la actualización de archivos después del análisis", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles)
      .mockResolvedValueOnce([
        {
          id: 10,
          originalName: "ventas.csv",
          storedName: "ventas.csv",
          fileType: "text/csv",
          fileSize: 100,
          status: "UPLOADED",
          createdAt: "2026-01-01T00:00:00Z",
          processedAt: null,
        },
      ])
      .mockRejectedValueOnce(new Error("No fue posible actualizar la lista."));

    vi.mocked(api.processFile).mockResolvedValue({
      rowCount: 4,
      columnCount: 2,
      missingCount: 0,
      duplicateCount: 0,
      columns: [],
      qualityIssues: [],
    });

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledTimes(1);
    });

    fireEvent.click(screen.getByRole("button", { name: "Analizar →" }));

    expect(
      await screen.findByRole("heading", { name: "Resultados del análisis" }),
    ).toBeInTheDocument();

    expect(
      await screen.findByText("4", { selector: ".stat-value" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(api.processFile).toHaveBeenCalledWith("test-token", 10);
      expect(api.getFiles).toHaveBeenCalledTimes(2);
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      "El análisis se completó, pero no fue posible actualizar la lista",
    );

    expect(
      screen.getByRole("heading", { name: "Detalle por columna" }),
    ).toBeInTheDocument();
  });

  it("informa si el archivo ya está siendo procesado sin iniciar otro análisis", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([
      {
        id: 10,
        originalName: "ventas.csv",
        storedName: "ventas.csv",
        fileType: "text/csv",
        fileSize: 100,
        status: "PROCESSING",
        createdAt: "2026-01-01T00:00:00Z",
        processedAt: null,
      },
    ]);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
    });

    fireEvent.click(screen.getByRole("button", { name: "En proceso..." }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Este archivo ya está siendo procesado.",
    );

    expect(api.processFile).not.toHaveBeenCalled();
    expect(api.getAnalysis).not.toHaveBeenCalled();
  });

  it("informa si el análisis anterior falló sin reintentarlo", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([
      {
        id: 10,
        originalName: "ventas.csv",
        storedName: "ventas.csv",
        fileType: "text/csv",
        fileSize: 100,
        status: "FAILED",
        createdAt: "2026-01-01T00:00:00Z",
        processedAt: null,
      },
    ]);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
    });

    fireEvent.click(screen.getByRole("button", { name: "Ver error →" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "El análisis anterior falló. No se reintentará automáticamente.",
    );

    expect(api.processFile).not.toHaveBeenCalled();
    expect(api.getAnalysis).not.toHaveBeenCalled();
  });

  it("muestra el error del backend y restablece el estado de procesamiento", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([
      {
        id: 10,
        originalName: "ventas.csv",
        storedName: "ventas.csv",
        fileType: "text/csv",
        fileSize: 100,
        status: "UPLOADED",
        createdAt: "2026-01-01T00:00:00Z",
        processedAt: null,
      },
    ]);

    let rejectProcess!: (reason: unknown) => void;

    vi.mocked(api.processFile).mockImplementation(
      () =>
        new Promise<Awaited<ReturnType<typeof api.processFile>>>(
          (_, reject) => {
            rejectProcess = reject;
          },
        ),
    );

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
    });

    fireEvent.click(screen.getByRole("button", { name: "Analizar →" }));

    expect(
      screen.getByRole("button", { name: "Analizando..." }),
    ).toBeDisabled();

    expect(screen.getByRole("status")).toHaveTextContent(
      "Analizando el archivo.",
    );

    rejectProcess(
      new ApiError("El archivo contiene un formato no válido.", 400),
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "El archivo contiene un formato no válido.",
    );

    expect(screen.queryByRole("status")).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "Analizar →" })).toBeEnabled();
    });

    expect(api.processFile).toHaveBeenCalledTimes(1);
  });
});
