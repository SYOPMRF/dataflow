import { beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import App from "./App";
import { ApiError, api } from "./services/api";

vi.mock("./services/api", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("./services/api")>();

  return {
    ...actual,
    api: {
      ...actual.api,
      login: vi.fn(),
      getFiles: vi.fn(),
    },
  };
});

describe("App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
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

    fireEvent.click(
      screen.getByRole("button", { name: /Entrar a DataFlow/i }),
    );

    await waitFor(() => {
      expect(api.getFiles).toHaveBeenCalledWith("test-token");
    });

    expect(
      await screen.findByRole("alert"),
    ).toHaveTextContent(
      "Tu sesión ha expirado. Inicia sesión nuevamente.",
    );

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();
  });
});
