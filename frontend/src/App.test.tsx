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
      deleteFile: vi.fn(),
      processFile: vi.fn(),
      getAnalysis: vi.fn(),
    },
  };
});

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
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
      columns: [
        {
          name: "edad",
          dataType: "int64",
          nullCount: 0,
          uniqueCount: 3,
          uniquePercentage: 75,
          duplicateCount: 1,
          duplicatedUniqueCount: 1,
          missingPercentage: 0,
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

    const columnSummary = screen.getByText("edad").closest("summary");

    expect(columnSummary).not.toBeNull();

    fireEvent.click(columnSummary!);

    expect(screen.getByText("Mínimo")).toBeInTheDocument();
    expect(screen.getByText("Máximo")).toBeInTheDocument();
    expect(screen.getByText("Mediana")).toBeInTheDocument();
    expect(screen.getByText("Desviación estándar")).toBeInTheDocument();
    expect(screen.getByText("Valores cero")).toBeInTheDocument();
    expect(screen.getByText("Valores negativos")).toBeInTheDocument();

    expect(screen.getByText("20")).toBeInTheDocument();
    expect(screen.getByText("30")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
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

  it("elimina un archivo después de confirmar la acción", async () => {
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

    vi.mocked(api.deleteFile).mockResolvedValue(undefined);
    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    expect(await screen.findByText("ventas.csv")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Eliminar" }));

    await waitFor(() => {
      expect(api.deleteFile).toHaveBeenCalledWith("test-token", 10);
    });

    await waitFor(() => {
      expect(screen.queryByText("ventas.csv")).not.toBeInTheDocument();
    });

    expect(window.confirm).toHaveBeenCalledWith(
      '¿Seguro que quieres eliminar "ventas.csv"? Esta acción no se puede deshacer.',
    );

    expect(
      await screen.findByText(/se eliminó correctamente/i),
    ).toBeInTheDocument();
  });
  it("conserva el archivo si el usuario cancela la eliminación", async () => {
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

    vi.spyOn(window, "confirm").mockReturnValue(false);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    expect(await screen.findByText("ventas.csv")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Eliminar" }));

    expect(window.confirm).toHaveBeenCalledOnce();
    expect(api.deleteFile).not.toHaveBeenCalled();
    expect(screen.getByText("ventas.csv")).toBeInTheDocument();
  });
  it("conserva el archivo e informa del error si falla la eliminación", async () => {
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

    vi.mocked(api.deleteFile).mockRejectedValue(
      new ApiError("Error interno del servidor", 500),
    );

    vi.spyOn(window, "confirm").mockReturnValue(true);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    expect(await screen.findByText("ventas.csv")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Eliminar" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "No fue posible eliminar el archivo: Error interno del servidor",
    );

    expect(api.deleteFile).toHaveBeenCalledWith("test-token", 10);
    expect(screen.getByText("ventas.csv")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Eliminar" })).toBeEnabled();
  });

  it("cierra la sesión y elimina la sesión persistida", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([]);

    render(<App />);

    fireEvent.change(screen.getByLabelText("Correo electrónico"), {
      target: { value: "test@dataflow.com" },
    });

    fireEvent.change(screen.getByLabelText("Contraseña"), {
      target: { value: "password123" },
    });

    fireEvent.click(screen.getByRole("button", { name: /Entrar a DataFlow/i }));

    // Esperar a que la sesión esté iniciada y el dashboard cargado.
    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
      expect(sessionStorage.getItem("dataflow.session")).not.toBeNull();
    });

    // Abrir el diálogo de confirmación.
    fireEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));

    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    expect(
      screen.getByRole("heading", { name: "¿Cerrar sesión?" }),
    ).toBeInTheDocument();

    // Cancelar no debe cerrar la sesión.
    fireEvent.click(screen.getByRole("button", { name: "Cancelar" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(sessionStorage.getItem("dataflow.session")).not.toBeNull();

    // Volver a abrir el diálogo y confirmar el cierre.
    fireEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));

    expect(await screen.findByRole("dialog")).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole("button", {
        name: "Confirmar cierre de sesión",
      }),
    );

    expect(
      await screen.findByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(sessionStorage.getItem("dataflow.session")).toBeNull();
    });

    expect(api.getFiles).toHaveBeenCalledTimes(1);
  });

  it("cierra el diálogo con Escape y devuelve el foco al botón de cierre de sesión", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([]);

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

    const logoutButton = screen.getByRole("button", {
      name: "Cerrar sesión",
    });

    fireEvent.click(logoutButton);

    await screen.findByRole("dialog");
    const cancelButton = screen.getByRole("button", { name: "Cancelar" });

    expect(cancelButton).toHaveFocus();

    fireEvent.keyDown(document, { key: "Escape" });

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(sessionStorage.getItem("dataflow.session")).not.toBeNull();
    expect(logoutButton).toHaveFocus();
  });

  it("mantiene el foco dentro del diálogo al navegar con Tab y Shift+Tab", async () => {
    vi.mocked(api.login).mockResolvedValue({
      token: "test-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    });

    vi.mocked(api.getFiles).mockResolvedValue([]);

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

    fireEvent.click(screen.getByRole("button", { name: "Cerrar sesión" }));

    await screen.findByRole("dialog");

    const cancelButton = screen.getByRole("button", { name: "Cancelar" });
    const confirmButton = screen.getByRole("button", {
      name: "Confirmar cierre de sesión",
    });

    expect(cancelButton).toHaveFocus();

    // Simular el foco en el último botón y comprobar que Tab vuelve al primero.
    confirmButton.focus();
    fireEvent.keyDown(document, { key: "Tab" });
    expect(cancelButton).toHaveFocus();

    // Simular el foco en el primer botón y comprobar que Shift+Tab vuelve al último.
    cancelButton.focus();
    fireEvent.keyDown(document, { key: "Tab", shiftKey: true });
    expect(confirmButton).toHaveFocus();
  });

  it("restaura la sesión almacenada y carga los archivos sin iniciar sesión de nuevo", async () => {
    const storedSession = {
      token: "restored-token",
      user: {
        id: 1,
        email: "test@dataflow.com",
        createdAt: "2026-01-01T00:00:00Z",
      },
    };

    sessionStorage.setItem("dataflow.session", JSON.stringify(storedSession));
    vi.mocked(api.getFiles).mockResolvedValue([]);

    render(<App />);

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("restored-token");
    });

    expect(api.login).not.toHaveBeenCalled();
    expect(await screen.findByText("test@dataflow.com")).toBeInTheDocument();
  });
  it("descarta una sesión almacenada corrupta y muestra el inicio de sesión", () => {
    sessionStorage.setItem("dataflow.session", "{json-invalido");

    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();

    expect(sessionStorage.getItem("dataflow.session")).toBeNull();
    expect(api.getFiles).not.toHaveBeenCalled();
  });
  it("descarta una sesión con datos de usuario incompletos", () => {
    sessionStorage.setItem(
      "dataflow.session",
      JSON.stringify({ token: "test-token", user: {} }),
    );

    render(<App />);

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();

    expect(sessionStorage.getItem("dataflow.session")).toBeNull();
    expect(api.getFiles).not.toHaveBeenCalled();
  });
});
