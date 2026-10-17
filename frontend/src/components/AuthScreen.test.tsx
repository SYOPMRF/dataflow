import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import AuthScreen from "./AuthScreen";
import { ApiError, api } from "../services/api";

vi.mock("../services/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../services/api")>();

  return {
    ...actual,
    api: {
      ...actual.api,
      login: vi.fn(),
      register: vi.fn(),
    },
  };
});

describe("AuthScreen", () => {
  const onAuthenticated = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("muestra el formulario de inicio de sesión inicialmente", () => {
    render(<AuthScreen onAuthenticated={onAuthenticated} />);

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: /Entrar a DataFlow/ }),
    ).toBeInTheDocument();

    expect(screen.getByLabelText("Correo electrónico")).toBeInTheDocument();

    expect(screen.getByLabelText("Contraseña")).toBeInTheDocument();
  });

  it("muestra el mensaje inicial cuando la sesión ha expirado", () => {
    render(
      <AuthScreen
        onAuthenticated={onAuthenticated}
        initialError="Tu sesión ha expirado. Inicia sesión nuevamente."
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Tu sesión ha expirado. Inicia sesión nuevamente.",
    );

    expect(
      screen.getByRole("heading", { name: "Inicia sesión" }),
    ).toBeInTheDocument();
  });

  it("notifica la sesión cuando el inicio de sesión es exitoso", async () => {
    const user = userEvent.setup();

    const session = {
      token: "token-de-prueba",
      user: {
        id: 1,
        email: "usuario@ejemplo.com",
        createdAt: "2026-10-16T12:00:00Z",
      },
    };

    vi.mocked(api.login).mockResolvedValue(session);

    render(<AuthScreen onAuthenticated={onAuthenticated} />);

    await user.type(
      screen.getByLabelText("Correo electrónico"),
      "usuario@ejemplo.com",
    );
    await user.type(screen.getByLabelText("Contraseña"), "claveSegura123");

    await user.click(screen.getByRole("button", { name: /Entrar a DataFlow/ }));

    await waitFor(() => {
      expect(onAuthenticated).toHaveBeenCalledWith(session);
    });

    expect(api.login).toHaveBeenCalledWith(
      "usuario@ejemplo.com",
      "claveSegura123",
    );
  });

  it("muestra un mensaje cuando las credenciales son incorrectas", async () => {
    const user = userEvent.setup();

    vi.mocked(api.login).mockRejectedValue(
      new ApiError("Credenciales inválidas", 401),
    );

    render(<AuthScreen onAuthenticated={onAuthenticated} />);

    await user.type(
      screen.getByLabelText("Correo electrónico"),
      "usuario@ejemplo.com",
    );
    await user.type(screen.getByLabelText("Contraseña"), "claveIncorrecta");
    await user.click(screen.getByRole("button", { name: /Entrar a DataFlow/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Correo o contraseña incorrectos.",
    );

    expect(onAuthenticated).not.toHaveBeenCalled();
  });

  it("impide registrar una cuenta si las contraseñas no coinciden", async () => {
    const user = userEvent.setup();

    render(<AuthScreen onAuthenticated={onAuthenticated} />);

    await user.click(screen.getByRole("button", { name: "Regístrate" }));

    expect(
      screen.getByRole("heading", { name: "Crea tu cuenta" }),
    ).toBeInTheDocument();

    await user.type(
      screen.getByLabelText("Correo electrónico"),
      "nuevo@ejemplo.com",
    );
    await user.type(screen.getByLabelText("Contraseña"), "claveSegura123");
    await user.type(
      screen.getByLabelText("Confirmar contraseña"),
      "otraClave123",
    );

    await user.click(screen.getByRole("button", { name: /Crear cuenta/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Las contraseñas no coinciden.",
    );

    expect(api.register).not.toHaveBeenCalled();
  });
});
