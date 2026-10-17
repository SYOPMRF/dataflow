import { useState, type FormEvent } from "react";
import { ApiError, api } from "../services/api";
import type { LoginResponse, UserResponse } from "../types/api";
import "./AuthScreen.css";

interface AuthScreenProps {
  onAuthenticated: (session: LoginResponse) => void;
  initialError?: string;
}

type AuthMode = "login" | "register";

function AuthScreen({ onAuthenticated, initialError = "" }: AuthScreenProps) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState(initialError);
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setSuccess("");

    const normalizedEmail = email.trim();

    if (mode === "register" && password !== confirmPassword) {
      setError("Las contraseñas no coinciden.");
      return;
    }

    setLoading(true);

    try {
      if (mode === "register") {
        const user: UserResponse = await api.register(
          normalizedEmail,
          password,
        );
        setSuccess(
          `Cuenta creada para ${user.email}. Ahora puedes iniciar sesión.`,
        );
        setMode("login");
        setPassword("");
        setConfirmPassword("");
      } else {
        const session = await api.login(normalizedEmail, password);
        onAuthenticated(session);
      }
    } catch (cause) {
      if (cause instanceof ApiError) {
        if (cause.status === 401) {
          setError("Correo o contraseña incorrectos.");
        } else if (cause.status === 409) {
          setError("Ya existe una cuenta con ese correo.");
        } else if (cause.status === 400) {
          setError(cause.message);
        } else {
          setError(cause.message);
        }
      } else {
        setError(
          "No fue posible conectar con el servidor. Inténtalo de nuevo.",
        );
      }
    } finally {
      setLoading(false);
    }
  };

  const switchMode = () => {
    setMode((current) => (current === "login" ? "register" : "login"));
    setError("");
    setSuccess("");
    setPassword("");
    setConfirmPassword("");
  };

  return (
    <main className="auth-screen">
      <section className="auth-brand-panel">
        <a className="auth-brand" href="/" aria-label="DataFlow inicio">
          <span className="auth-brand-mark">↗</span>
          <span>
            data<span>flow</span>
          </span>
        </a>

        <div className="auth-brand-copy">
          <span className="auth-eyebrow">CLARIDAD EN CADA DATO</span>
          <h1>Entiende tus datos. Decide con confianza.</h1>
          <p>
            Explora tus archivos, descubre patrones y detecta problemas de
            calidad desde un solo lugar.
          </p>
          <div className="auth-feature-list">
            <div>
              <span>✓</span> Análisis automático de tus archivos
            </div>
            <div>
              <span>✓</span> Estadísticas claras y accionables
            </div>
            <div>
              <span>✓</span> Identificación de problemas de calidad
            </div>
          </div>
        </div>

        <span className="auth-panel-footer">
          DATAFLOW · INSIGHT STARTS HERE
        </span>
      </section>

      <section className="auth-form-panel">
        <div className="auth-form-container">
          <div className="auth-mobile-brand">
            <span className="auth-brand-mark">↗</span>
            <span>
              data<span>flow</span>
            </span>
          </div>

          <span className="auth-form-eyebrow">
            {mode === "login"
              ? "QUÉ BUENO TENERTE DE VUELTA"
              : "EMPIEZA TU ESPACIO DE DATOS"}
          </span>
          <h2>{mode === "login" ? "Inicia sesión" : "Crea tu cuenta"}</h2>
          <p className="auth-form-description">
            {mode === "login"
              ? "Ingresa tus datos para continuar a tu espacio de trabajo."
              : "Regístrate para empezar a explorar tus datos."}
          </p>

          <form onSubmit={handleSubmit} className="auth-form">
            <label htmlFor="auth-email">Correo electrónico</label>
            <input
              id="auth-email"
              type="email"
              autoComplete="email"
              placeholder="tu@correo.com"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              maxLength={254}
              required
            />

            <label htmlFor="auth-password">Contraseña</label>
            <input
              id="auth-password"
              type="password"
              autoComplete={
                mode === "login" ? "current-password" : "new-password"
              }
              placeholder={
                mode === "register" ? "Mínimo 8 caracteres" : "Tu contraseña"
              }
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              minLength={mode === "register" ? 8 : undefined}
              maxLength={100}
              required
            />

            {mode === "register" && (
              <>
                <label htmlFor="auth-confirm-password">
                  Confirmar contraseña
                </label>
                <input
                  id="auth-confirm-password"
                  type="password"
                  autoComplete="new-password"
                  placeholder="Repite tu contraseña"
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  minLength={8}
                  maxLength={100}
                  required
                />
              </>
            )}

            {error && (
              <p className="auth-message auth-error" role="alert">
                {error}
              </p>
            )}
            {success && (
              <p className="auth-message auth-success" role="status">
                {success}
              </p>
            )}

            <button className="auth-submit" type="submit" disabled={loading}>
              {loading
                ? "Procesando…"
                : mode === "login"
                  ? "Entrar a DataFlow"
                  : "Crear cuenta"}
              {!loading && <span>→</span>}
            </button>
          </form>

          <p className="auth-mode-switch">
            {mode === "login"
              ? "¿Todavía no tienes cuenta?"
              : "¿Ya tienes una cuenta?"}{" "}
            <button type="button" onClick={switchMode} disabled={loading}>
              {mode === "login" ? "Regístrate" : "Inicia sesión"}
            </button>
          </p>

          <p className="auth-security-note">
            Tus datos están protegidos por la autenticación de DataFlow.
          </p>
        </div>
      </section>
    </main>
  );
}

export default AuthScreen;
