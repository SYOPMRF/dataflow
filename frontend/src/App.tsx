import AppIcon, { type AppIconName } from "./components/AppIcon";
import { useEffect, useRef, useState } from "react";
import AuthScreen from "./components/AuthScreen";
import { ApiError, api } from "./services/api";
import type { AnalysisResult, FileResponse, LoginResponse } from "./types/api";
import "./App.css";
import DistributionAnalysis from "./components/DistributionAnalysis";
import CorrelationAnalysis from "./components/CorrelationAnalysis";

const SESSION_STORAGE_KEY = "dataflow.session";

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatFileDate(value: string): string {
  return new Intl.DateTimeFormat("es-CO", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function formatAnalysisValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "—";
  }

  if (typeof value === "number") {
    return Number.isFinite(value)
      ? value.toLocaleString("es-CO", { maximumFractionDigits: 2 })
      : "—";
  }

  if (typeof value === "string") {
    return value.trim() || "—";
  }

  if (typeof value === "boolean") {
    return value ? "Sí" : "No";
  }

  return "—";
}

function getFileStatus(status: FileResponse["status"]) {
  switch (status) {
    case "COMPLETED":
      return { label: "Completado", className: "status-complete" };
    case "PROCESSING":
      return { label: "Procesando", className: "status-processing" };
    case "FAILED":
      return { label: "Error", className: "status-error" };
    case "UPLOADED":
      return { label: "Pendiente", className: "status-pending" };
  }
}

const navigation: { label: string; icon: AppIconName }[] = [
  { label: "Resumen", icon: "dashboard" },
  { label: "Mis archivos", icon: "files" },
];

function App() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const logoutTriggerRef = useRef<HTMLButtonElement>(null);
  const logoutCancelRef = useRef<HTMLButtonElement>(null);
  const [activePage, setActivePage] = useState("Resumen");
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [notice, setNotice] = useState("");
  const [noticeType, setNoticeType] = useState<"info" | "error">("info");
  const [dragging, setDragging] = useState(false);
  const [session, setSession] = useState<LoginResponse | null>(() => {
    try {
      const storedSession = sessionStorage.getItem(SESSION_STORAGE_KEY);

      if (!storedSession) {
        return null;
      }

      const parsedSession: unknown = JSON.parse(storedSession);

      if (
        typeof parsedSession !== "object" ||
        parsedSession === null ||
        !("token" in parsedSession) ||
        typeof parsedSession.token !== "string" ||
        parsedSession.token.trim().length === 0 ||
        !("user" in parsedSession) ||
        typeof parsedSession.user !== "object" ||
        parsedSession.user === null ||
        !("id" in parsedSession.user) ||
        typeof parsedSession.user.id !== "number" ||
        !Number.isFinite(parsedSession.user.id) ||
        !("email" in parsedSession.user) ||
        typeof parsedSession.user.email !== "string" ||
        parsedSession.user.email.trim().length === 0 ||
        !("createdAt" in parsedSession.user) ||
        typeof parsedSession.user.createdAt !== "string" ||
        parsedSession.user.createdAt.trim().length === 0
      ) {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
        return null;
      }

      return parsedSession as LoginResponse;
    } catch {
      sessionStorage.removeItem(SESSION_STORAGE_KEY);
      return null;
    }
  });
  const [files, setFiles] = useState<FileResponse[]>([]);
  const [filesReloadKey, setFilesReloadKey] = useState(0);
  const [filesLoading, setFilesLoading] = useState(false);
  const [filesError, setFilesError] = useState("");
  const [uploading, setUploading] = useState(false);
  const [processingFileId, setProcessingFileId] = useState<number | null>(null);
  const [deletingFileId, setDeletingFileId] = useState<number | null>(null);
  const [analysisFileId, setAnalysisFileId] = useState<number | null>(null);
  const [analysisResult, setAnalysisResult] = useState<AnalysisResult | null>(
    null,
  );
  const [analysisFileName, setAnalysisFileName] = useState("");
  const [analysisError, setAnalysisError] = useState("");
  const [showLogoutDialog, setShowLogoutDialog] = useState(false);

  const handleSessionExpired = () => {
    setSession(null);
    setFiles([]);
    setSelectedFile(null);
    setAnalysisResult(null);
    setAnalysisFileName("");
    setAnalysisError("");
    setNotice("");
    setFilesError("Tu sesión ha expirado. Inicia sesión nuevamente.");
  };

  const handleLogout = () => {
    setSession(null);
    setFiles([]);
    setSelectedFile(null);
    setAnalysisFileId(null);
    setAnalysisResult(null);
    setAnalysisFileName("");
    setAnalysisError("");
    setNotice("");
    setFilesError("");
    setActivePage("Resumen");
  };

  useEffect(() => {
    try {
      if (session) {
        sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session));
      } else {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
      }
    } catch {
      // El almacenamiento del navegador puede estar bloqueado o no disponible.
    }
  }, [session]);

  useEffect(() => {
    if (!showLogoutDialog) return;

    const logoutTrigger = logoutTriggerRef.current;

    logoutCancelRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setShowLogoutDialog(false);
        return;
      }

      if (event.key !== "Tab") return;

      const dialog = document.querySelector(".logout-dialog");
      if (!dialog) return;

      const focusableElements = dialog.querySelectorAll<HTMLElement>(
        'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );

      if (focusableElements.length === 0) {
        event.preventDefault();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      if (event.shiftKey && document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      } else if (!event.shiftKey && document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      logoutTrigger?.focus();
    };
  }, [showLogoutDialog]);

  useEffect(() => {
    if (!session) return;

    const token = session.token;
    let cancelled = false;

    async function loadFiles() {
      setFilesLoading(true);
      setFilesError("");

      try {
        const result = await api.getFiles(token);

        if (!cancelled) {
          setFiles(result);
        }
      } catch (error) {
        if (!cancelled) {
          if (error instanceof ApiError && error.status === 401) {
            handleSessionExpired();
          } else {
            setFilesError(
              error instanceof Error
                ? error.message
                : "No fue posible cargar los archivos.",
            );
          }
        }
      } finally {
        if (!cancelled) {
          setFilesLoading(false);
        }
      }
    }

    void loadFiles();

    return () => {
      cancelled = true;
    };
  }, [session, filesReloadKey]);

  const chooseFile = (file?: File) => {
    if (!file) return;

    const extension = file.name.split(".").pop()?.toLowerCase();
    if (!["csv", "xlsx"].includes(extension ?? "")) {
      setSelectedFile(null);
      setNoticeType("error");
      setNotice("Formato no válido. Selecciona un archivo CSV o XLSX.");
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      setSelectedFile(null);
      setNoticeType("error");
      setNotice("El archivo supera el límite de 10 MB.");
      return;
    }

    setSelectedFile(file);
    setNoticeType("info");
    setNotice("Archivo seleccionado. Está listo para subir.");
  };

  const openFilePicker = () => fileInputRef.current?.click();

  const uploadSelectedFile = async () => {
    if (!session || !selectedFile || uploading) return;

    setUploading(true);
    setNotice("");

    try {
      try {
        await api.uploadFile(session.token, selectedFile);
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          handleSessionExpired();
        } else {
          setNoticeType("error");
          setNotice(
            error instanceof Error
              ? error.message
              : "No fue posible cargar el archivo.",
          );
        }

        return;
      }

      setSelectedFile(null);
      setNoticeType("info");
      setNotice("Archivo cargado correctamente.");

      try {
        const updatedFiles = await api.getFiles(session.token);
        setFiles(updatedFiles);
        setFilesError("");
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          handleSessionExpired();
        } else {
          setFilesError(
            error instanceof Error
              ? `El archivo se cargó correctamente, pero no fue posible actualizar la lista: ${error.message}`
              : "El archivo se cargó correctamente, pero no fue posible actualizar la lista.",
          );
          setNotice("");
          setActivePage("Mis archivos");
        }
      }
    } finally {
      setUploading(false);
    }
  };

  const analyzeFile = async (file: FileResponse) => {
    if (!session || processingFileId !== null) return;

    setAnalysisError("");
    setAnalysisResult(null);
    setAnalysisFileName(file.originalName);
    setAnalysisFileId(file.id);
    setActivePage("Resultados");

    if (file.status === "PROCESSING") {
      setAnalysisError("Este archivo ya está siendo procesado.");
      return;
    }

    if (file.status === "FAILED") {
      setAnalysisError(
        "El análisis anterior falló. No se reintentará automáticamente.",
      );
      return;
    }

    setProcessingFileId(file.id);

    try {
      const result =
        file.status === "COMPLETED"
          ? await api.getAnalysis(session.token, file.id)
          : await api.processFile(session.token, file.id);

      setAnalysisResult(result);

      try {
        const updatedFiles = await api.getFiles(session.token);
        setFiles(updatedFiles);
        setFilesError("");
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          handleSessionExpired();
        } else {
          setAnalysisError(
            error instanceof Error
              ? `El análisis se completó, pero no fue posible actualizar la lista: ${error.message}`
              : "El análisis se completó, pero no fue posible actualizar la lista.",
          );
        }
      }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        handleSessionExpired();
      } else {
        setAnalysisError(
          error instanceof Error
            ? error.message
            : "No fue posible analizar el archivo.",
        );
      }
    } finally {
      setProcessingFileId(null);
    }
  };

  const deleteFile = async (file: FileResponse) => {
    if (!session || deletingFileId !== null || processingFileId !== null) {
      return;
    }

    const confirmed = window.confirm(
      `¿Seguro que quieres eliminar "${file.originalName}"? Esta acción no se puede deshacer.`,
    );

    if (!confirmed) return;

    setDeletingFileId(file.id);
    setNotice("");

    try {
      await api.deleteFile(session.token, file.id);

      setFiles((currentFiles) =>
        currentFiles.filter((currentFile) => currentFile.id !== file.id),
      );

      if (analysisFileId === file.id) {
        setAnalysisFileId(null);
        setAnalysisResult(null);
        setAnalysisFileName("");
        setAnalysisError("");
        setActivePage("Mis archivos");
      }

      setNoticeType("info");
      setNotice(`"${file.originalName}" se eliminó correctamente.`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        handleSessionExpired();
      } else {
        setNoticeType("error");
        setNotice(
          error instanceof Error
            ? `No fue posible eliminar el archivo: ${error.message}`
            : "No fue posible eliminar el archivo.",
        );
      }
    } finally {
      setDeletingFileId(null);
    }
  };

  if (!session) {
    return (
      <AuthScreen
        onAuthenticated={setSession}
        initialError={filesError === "" ? "" : filesError}
      />
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a
          className="brand"
          href="#"
          onClick={(event) => {
            event.preventDefault();
            setActivePage("Resumen");
          }}
        >
          <span className="brand-mark">
            <span><AppIcon name="chart" size={22} /></span>
          </span>
          <span>
            data<span className="brand-light">flow</span>
          </span>
        </a>

        <div className="workspace-label">ESPACIO DE TRABAJO</div>

        <nav className="sidebar-nav" aria-label="Navegación principal">
          {navigation.map((item) => (
            <button
              key={item.label}
              className={`nav-item ${activePage === item.label ? "active" : ""}`}
              onClick={() => setActivePage(item.label)}
            >
              <span className="nav-icon">
                <AppIcon name={item.icon} size={19} />
              </span>
              {item.label}
              {item.label === "Mis archivos" && (
                <span className="nav-count">{files.length}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon"><AppIcon name="sparkles" size={20} /></div>
            <strong>¿Primera vez aquí?</strong>
            <p>Sube tus datos y descubre qué contienen.</p>
            <button onClick={() => setActivePage("Nuevo análisis")}>
              Analizar un archivo <span>→</span>
            </button>
          </div>
          <div className="profile">
            <div className="avatar">DF</div>
            <div className="profile-copy">
              <strong>{session.user.email}</strong>
              <span>Plan gratuito</span>
            </div>
            <button
              ref={logoutTriggerRef}
              className="profile-menu"
              type="button"
              aria-label="Cerrar sesión"
              title="Cerrar sesión"
              onClick={() => setShowLogoutDialog(true)}
            >
              <span aria-hidden="true">
                <AppIcon name="logout" size={18} />
              </span>
              <span className="profile-menu-label">Cerrar sesión</span>
            </button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumbs">
            <span>Workspace</span>
            <span className="breadcrumb-separator">/</span>
            <strong>{activePage}</strong>
          </div>
          <div className="topbar-right">
            <span className="environment">
              <span /> Entorno de desarrollo
            </span>
            <button
              className="icon-button"
              aria-label="Notificaciones"
              title="Notificaciones"
            >
              <AppIcon name="bell" size={19} />
            </button>
            <div className="top-avatar">DF</div>
          </div>
        </header>

        <div className="page-content">

        <input
          ref={fileInputRef}
          className="visually-hidden"
          type="file"
          accept=".csv,.xlsx"
          onChange={(event) => {
            chooseFile(event.target.files?.[0]);
            event.target.value = "";
          }}
        />

        {notice && (
          <p
            className={`notice ${noticeType === "info" ? "notice-info" : "notice-error"}`}
            role={noticeType === "error" ? "alert" : "status"}
          >
            {notice}
          </p>
        )}

        {activePage !== "Resultados" && (

          <section className="welcome-section">
            <div>
              <div className="eyebrow">
                <span className="eyebrow-dot" /> TU ESPACIO DE DATOS
              </div>
              <h1>
                {activePage === "Resumen"
                  ? "Tus datos, más claros."
                  : activePage === "Mis archivos"
                    ? "Mis archivos."
                    : "Nuevo análisis."}
              </h1>
              <p className="welcome-description">
                {activePage === "Resumen"
                  ? "Sube un archivo y deja que el análisis comience."
                  : activePage === "Mis archivos"
                    ? "Consulta tus archivos y prepara tus próximos análisis."
                    : "Selecciona un archivo CSV o XLSX para comenzar."}
              </p>
            </div>
            <button className="primary-button" onClick={() => setActivePage("Nuevo análisis")}>
              <span className="button-plus">＋</span> Nuevo análisis
            </button>
          </section>
        )}

        {activePage === "Resumen" && (
          <section className="dashboard-overview" aria-label="Resumen de actividad">
            <div className="dashboard-metrics">
              <article className="dashboard-metric dashboard-metric-total">
                <div className="dashboard-metric-heading">
                  <span>ARCHIVOS CARGADOS</span>
                  <span className="dashboard-metric-icon">
                    <AppIcon name="files" size={20} />
                  </span>
                </div>
                <strong>{files.length.toLocaleString("es-CO")}</strong>
                <p>Archivos almacenados en tu espacio de trabajo</p>
              </article>

              <article className="dashboard-metric dashboard-metric-completed">
                <div className="dashboard-metric-heading">
                  <span>ANÁLISIS COMPLETADOS</span>
                  <span className="dashboard-metric-icon">
                    <AppIcon name="check" size={20} />
                  </span>
                </div>
                <strong>
                  {files.filter((file) => file.status === "COMPLETED").length.toLocaleString("es-CO")}
                </strong>
                <p>Análisis disponibles para consultar</p>
              </article>
            </div>

            <section className="dashboard-status-panel" aria-labelledby="status-panel-title">
              <div className="dashboard-panel-heading">
                <div>
                  <span className="dashboard-panel-eyebrow">ACTIVIDAD</span>
                  <h2 id="status-panel-title">Estado de tus archivos</h2>
                </div>
                <span className="dashboard-total-label">
                  {files.length.toLocaleString("es-CO")} en total
                </span>
              </div>

              <div className="dashboard-status-list">
                {[
                  { status: "COMPLETED", label: "Completados", className: "completed", icon: "check" as const },
                  { status: "PROCESSING", label: "Procesando", className: "processing", icon: "clock" as const },
                  { status: "UPLOADED", label: "Pendientes", className: "pending", icon: "upload" as const },
                  { status: "FAILED", label: "Con errores", className: "failed", icon: "alert" as const },
                ].map((item) => {
                  const count = files.filter((file) => file.status === item.status).length;
                  const percentage = files.length === 0 ? 0 : (count / files.length) * 100;

                  return (
                    <div className="dashboard-status-item" key={item.status}>
                      <div className="dashboard-status-info">
                        <span className={`dashboard-status-icon ${item.className}`}>
                          <AppIcon name={item.icon} size={16} />
                        </span>
                        <span>{item.label}</span>
                        <strong>{count.toLocaleString("es-CO")}</strong>
                      </div>
                      <div
                        className="dashboard-status-track"
                        role="progressbar"
                        aria-label={`${item.label}: ${count} archivos`}
                        aria-valuenow={count}
                        aria-valuemin={0}
                        aria-valuemax={files.length}
                      >
                        <span
                          className={`dashboard-status-fill ${item.className}`}
                          style={{ width: `${percentage}%` }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>
          </section>
        )}
        {activePage === "Nuevo análisis" && (
          <section className="upload-section">
            <div className="section-heading">
              <div>
                <h2>Comienza con tus datos</h2>
                <p>Un archivo es todo lo que necesitas para empezar.</p>
              </div>
              <span className="step-label">
                <span>01</span> CARGA
              </span>
            </div>

            <div
              className={`upload-zone ${dragging ? "dragging" : ""}`}
              onDragOver={(event) => {
                event.preventDefault();
                setDragging(true);
              }}
              onDragLeave={() => setDragging(false)}
              onDrop={(event) => {
                event.preventDefault();
                setDragging(false);
                chooseFile(event.dataTransfer.files[0]);
              }}
            >
              <div className="upload-icon">
                <span>↑</span>
              </div>
              <h3>Arrastra tu archivo aquí</h3>
              <p>o selecciona un archivo desde tu equipo</p>
              <button className="secondary-button" onClick={openFilePicker}>
                Explorar archivos
              </button>
              <div className="file-requirements">
                <span>
                  <b>CSV</b> .csv
                </span>
                <span>
                  <b>XLSX</b> .xlsx
                </span>
                <span>Máximo 10 MB</span>
              </div>
            </div>

            {selectedFile && (
              <div className="selected-file">
                <span className="file-type-icon">
                  {selectedFile.name.toLowerCase().endsWith(".csv")
                    ? "CSV"
                    : "XLS"}
                </span>
                <div className="selected-file-copy">
                  <strong>{selectedFile.name}</strong>
                  <span>
                    {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB ·
                    Seleccionado localmente
                  </span>
                </div>
                <button
                  className="remove-file"
                  onClick={() => {
                    setSelectedFile(null);
                    setNotice("");
                  }}
                  aria-label="Quitar archivo"
                >
                  ×
                </button>
                <button
                  className="primary-button"
                  type="button"
                  onClick={uploadSelectedFile}
                  disabled={uploading}
                >
                  {uploading ? "Subiendo..." : "Subir archivo"}
                </button>
              </div>
            )}

          </section>
        )}

        {(activePage === "Resumen" || activePage === "Mis archivos") && (
          <section className="files-section">
            {activePage === "Mis archivos" ? (
              <div className="library-heading">
                <div className="library-heading-top">
                  <div className="library-title-group">
                    <span className="library-eyebrow">DATA WORKSPACE / BIBLIOTECA</span>
                    <h2>Mis archivos</h2>
                    <p>Gestiona tus conjuntos de datos y consulta sus análisis.</p>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => setActivePage("Resumen")}
                  >
                    Volver al resumen <span>→</span>
                  </button>
                </div>
                <div className="library-metrics">
                  <div className="library-metric">
                    <span className="library-metric-label">TOTAL DE ARCHIVOS</span>
                    <strong>{files.length.toLocaleString("es-CO")}</strong>
                  </div>
                  <div className="library-metric">
                    <span className="library-metric-label">COMPLETADOS</span>
                    <strong>{files.filter((file) => file.status === "COMPLETED").length.toLocaleString("es-CO")}</strong>
                  </div>
                  <div className="library-metric">
                    <span className="library-metric-label">PENDIENTES</span>
                    <strong>{files.filter((file) => file.status === "UPLOADED").length.toLocaleString("es-CO")}</strong>
                  </div>
                </div>
              </div>
            ) : (
              <div className="section-heading files-heading">
                <div>
                  <h2>Archivos recientes</h2>
                  <p>Un vistazo rápido a tu actividad reciente.</p>
                </div>
                <button
                  className="text-button"
                  onClick={() => setActivePage("Mis archivos")}
                >
                  Ver todos <span>→</span>
                </button>
              </div>
            )}

            <div className="files-table">
              <div className="table-header">
                <span>ARCHIVO</span>
                <span>FECHA</span>
                <span>TAMAÑO</span>
                <span>ESTADO</span>
                <span className="table-actions-heading">ACCIONES</span>
              </div>

              {filesLoading && (
                <p className="notice notice-info" role="status">
                  Cargando tus archivos…
                </p>
              )}

              {!filesLoading && filesError && (
                <div className="notice notice-error" role="alert">
                  <p>{filesError}</p>
                  <button
                    className="text-button"
                    onClick={() => {
                      setFilesReloadKey((current) => current + 1);
                    }}
                  >
                    Intentar de nuevo →
                  </button>
                </div>
              )}

              {!filesLoading && !filesError && files.length === 0 && (
                <p className="notice notice-info" role="status">
                  Aún no tienes archivos. Sube un CSV o XLSX para comenzar.
                </p>
              )}

              {!filesLoading &&
                !filesError &&
                (activePage === "Resumen" ? files.slice(0, 5) : files).map((file) => {
                  const status = getFileStatus(file.status);
                  const isCsv = file.originalName
                    .toLowerCase()
                    .endsWith(".csv");

                  return (
                    <div className="file-row" key={file.id}>
                      <div className="file-name-cell">
                        <span
                          className={`file-type-icon ${isCsv ? "csv-icon" : ""}`}
                        >
                          {isCsv ? "CSV" : "XLS"}
                        </span>

                        <div className="file-name-copy">
                          <strong>{file.originalName}</strong>
                          <span>Archivo de tu cuenta</span>
                        </div>
                      </div>

                      <span className="file-date">
                        {formatFileDate(file.createdAt)}
                      </span>

                      <span className="file-size">
                        {formatFileSize(file.fileSize)}
                      </span>

                      <span>
                        <span className={`status-pill ${status.className}`}>
                          <span />
                          {status.label}
                        </span>
                      </span>

                      <button
                        className="text-button"
                        type="button"
                        onClick={() => void analyzeFile(file)}
                        disabled={processingFileId !== null}
                      >
                        {processingFileId === file.id
                          ? "Analizando..."
                          : file.status === "COMPLETED"
                            ? "Ver resultados →"
                            : file.status === "FAILED"
                              ? "Ver error →"
                              : file.status === "PROCESSING"
                                ? "En proceso..."
                                : "Analizar →"}
                      </button>

                      <button
                        className="text-button"
                        type="button"
                        onClick={() => void deleteFile(file)}
                        disabled={
                          uploading ||
                          deletingFileId !== null ||
                          processingFileId !== null ||
                          file.status === "PROCESSING"
                        }
                      >
                        {deletingFileId === file.id
                          ? "Eliminando..."
                          : "Eliminar"}
                      </button>
                    </div>
                  );
                })}
            </div>
          </section>
        )}

          {activePage === "Resultados" && (
            <section className="files-section" aria-labelledby="results-title">
              <div className="section-heading">
                <div>
                  <h2 id="results-title">Resultados del análisis</h2>
                  <p>{analysisFileName}</p>
                </div>
                <button
                  className="text-button"
                  onClick={() => setActivePage("Mis archivos")}
                >
                  Volver a mis archivos →
                </button>
              </div>

              {processingFileId !== null && (
                <p className="notice notice-info" role="status">
                  Analizando el archivo. Esto puede tardar unos segundos...
                </p>
              )}

              {analysisError && (
                <p className="notice notice-error" role="alert">
                  {analysisError}
                </p>
              )}

              {analysisResult && (
                <>
                  <div className="stats-grid">
                    <article className="stat-card">
                      <div className="stat-top">
                        <span>Filas</span>
                      </div>
                      <div className="stat-value">
                        {analysisResult.rowCount.toLocaleString("es-CO")}
                      </div>
                    </article>

                    <article className="stat-card">
                      <div className="stat-top">
                        <span>Columnas</span>
                      </div>
                      <div className="stat-value">
                        {analysisResult.columnCount.toLocaleString("es-CO")}
                      </div>
                    </article>

                    <article className="stat-card">
                      <div className="stat-top">
                        <span>Valores faltantes</span>
                      </div>
                      <div className="stat-value">
                        {analysisResult.missingCount.toLocaleString("es-CO")}
                      </div>
                    </article>

                    <article className="stat-card">
                      <div className="stat-top">
                        <span>Filas duplicadas</span>
                      </div>
                      <div className="stat-value">
                        {analysisResult.duplicateCount.toLocaleString("es-CO")}
                      </div>
                    </article>
                  </div>

                  <div className="files-section">
                    <div className="section-heading">
                      <div>
                        <h2>Detalle por columna</h2>
                        <p>
                          Tipos de datos, valores únicos y estadísticas
                          numéricas.
                        </p>
                      </div>
                    </div>

                    <div className="analysis-table">
                      <div className="analysis-table-header">
                        <span>COLUMNA</span>
                        <span>TIPO</span>
                        <span>FALTANTES</span>
                        <span>ÚNICOS</span>
                        <span>MEDIA</span>
                      </div>

                      {analysisResult.columns.map((column) => (
                        <details className="analysis-column" key={column.name}>
                          <summary className="analysis-column-summary">
                            <strong>{column.name}</strong>

                            <span>{column.dataType}</span>

                            <span>
                              {column.nullCount.toLocaleString("es-CO")}
                              {" ("}
                              {column.missingPercentage.toLocaleString(
                                "es-CO",
                                {
                                  maximumFractionDigits: 2,
                                },
                              )}
                              %)
                            </span>

                            <span>
                              {column.uniqueCount.toLocaleString("es-CO")}
                            </span>

                            <span>{formatAnalysisValue(column.meanValue)}</span>

                            <span className="analysis-expand-label">
                              <span className="analysis-expand-closed">
                                Ver estadísticas
                              </span>
                              <span className="analysis-expand-open">
                                Ocultar estadísticas
                              </span>
                              <span
                                aria-hidden="true"
                                className="analysis-expand-icon"
                              >
                                +
                              </span>
                            </span>
                          </summary>

                          <div className="analysis-column-details">
                            <div className="analysis-metric">
                              <span>Mínimo</span>
                              <strong>
                                {formatAnalysisValue(column.minValue)}
                              </strong>
                            </div>

                            <div className="analysis-metric">
                              <span>Máximo</span>
                              <strong>
                                {formatAnalysisValue(column.maxValue)}
                              </strong>
                            </div>

                            <div className="analysis-metric">
                              <span>Mediana</span>
                              <strong>
                                {formatAnalysisValue(column.medianValue)}
                              </strong>
                            </div>

                            <div className="analysis-metric">
                              <span>Desviación estándar</span>
                              <strong>
                                {formatAnalysisValue(column.stdDeviation)}
                              </strong>
                            </div>

                            <div className="analysis-metric">
                              <span>Valores cero</span>
                              <strong>
                                {column.zeroCount.toLocaleString("es-CO")}
                              </strong>
                            </div>

                            <div className="analysis-metric">
                              <span>Valores negativos</span>
                              <strong>
                                {column.negativeCount.toLocaleString("es-CO")}
                              </strong>
                            </div>
                          {column.q1Value !== null && (
                            <div className="analysis-metric">
                              <span>Primer cuartil (Q1)</span>
                              <strong>{formatAnalysisValue(column.q1Value)}</strong>
                            </div>
                          )}

                          {column.q3Value !== null && (
                            <div className="analysis-metric">
                              <span>Tercer cuartil (Q3)</span>
                              <strong>{formatAnalysisValue(column.q3Value)}</strong>
                            </div>
                          )}

                          {column.iqrValue !== null && (
                            <div className="analysis-metric">
                              <span>Rango intercuartílico (IQR)</span>
                              <strong>{formatAnalysisValue(column.iqrValue)}</strong>
                            </div>
                          )}

                          {column.p5Value !== null && (
                            <div className="analysis-metric">
                              <span>Percentil 5</span>
                              <strong>{formatAnalysisValue(column.p5Value)}</strong>
                            </div>
                          )}

                          {column.p10Value !== null && (
                            <div className="analysis-metric">
                              <span>Percentil 10</span>
                              <strong>{formatAnalysisValue(column.p10Value)}</strong>
                            </div>
                          )}

                          {column.p90Value !== null && (
                            <div className="analysis-metric">
                              <span>Percentil 90</span>
                              <strong>{formatAnalysisValue(column.p90Value)}</strong>
                            </div>
                          )}

                          {column.p95Value !== null && (
                            <div className="analysis-metric">
                              <span>Percentil 95</span>
                              <strong>{formatAnalysisValue(column.p95Value)}</strong>
                            </div>
                          )}

                          {column.outlierCount !== null && (
                            <div className="analysis-metric">
                              <span>Valores atípicos</span>
                              <strong>
                                {column.outlierCount.toLocaleString("es-CO")}
                              </strong>
                            </div>
                          )}
                          </div>
                        </details>
                      ))}
                    </div>
                  </div>

                  <DistributionAnalysis
                    analysis={analysisResult.distributionAnalysis}
                  />

                  <CorrelationAnalysis
                    analysis={analysisResult.correlationAnalysis}
                  />

                  <div className="files-section">
                    <div className="section-heading">
                      <div>
                        <h2>Análisis de duplicados</h2>
                        <p>
                          Claves candidatas y valores que se repiten en columnas individuales.
                          Las repeticiones no necesariamente indican errores en los datos.
                        </p>
                      </div>
                    </div>

                    <div className="duplicate-analysis-grid">
                      <article className="duplicate-analysis-card">
                        <div className="duplicate-analysis-heading">
                          <h3>Claves candidatas</h3>
                          <span>
                            {analysisResult.duplicateAnalysis.candidateKeys.length.toLocaleString(
                              "es-CO",
                            )}
                          </span>
                        </div>

                        {analysisResult.duplicateAnalysis.candidateKeys.length === 0 ? (
                          <p className="notice notice-info" role="status">
                            No se identificaron combinaciones que cumplan los
                            criterios de clave candidata.
                          </p>
                        ) : (
                          <div className="duplicate-analysis-list">
                            {!analysisResult.duplicateAnalysis.candidateKeys.some(
                              (candidate) => candidate.keyType === "SINGLE",
                            ) && (
                              <p className="notice notice-info" role="status">
                                No se identificaron claves individuales que sean únicas
                                en las filas evaluadas.
                              </p>
                            )}
                            {analysisResult.duplicateAnalysis.candidateKeys
                                .filter((candidate) => candidate.keyType === "SINGLE")
                                .slice(0, 10)
                                .map((candidate, index) => (
                                <article
                                  className="duplicate-analysis-item"
                                  key={`${candidate.columns.join("-")}-${index}`}
                                >
                                  <strong>{candidate.columns.join(" + ")}</strong>

                                  <span>Clave individual</span>

                                  <div className="duplicate-analysis-metadata">
                                    <span>
                                      Unicidad:{" "}
                                      {formatAnalysisValue(candidate.uniquenessPercentage)}%
                                    </span>
                                    <span>
                                      Filas evaluadas:{" "}
                                      {candidate.evaluatedRowCount.toLocaleString("es-CO")}
                                    </span>
                                    <span>
                                      Valores únicos:{" "}
                                      {candidate.uniqueCount.toLocaleString("es-CO")}
                                    </span>
                                  </div>
                                </article>
                              ))}

                            {analysisResult.duplicateAnalysis.candidateKeys.some(
                              (candidate) => candidate.keyType !== "SINGLE",
                            ) && (
                              <details className="duplicate-analysis-item">
                                <summary>
                                  <strong>Combinaciones únicas detectadas</strong>
                                  <span>
                                    {
                                      analysisResult.duplicateAnalysis.candidateKeys.filter(
                                        (candidate) => candidate.keyType !== "SINGLE",
                                      ).length
                                    }{" "}
                                    combinaciones
                                  </span>
                                </summary>

                                <div className="duplicate-analysis-list">
                                  {analysisResult.duplicateAnalysis.candidateKeys
                                    .filter((candidate) => candidate.keyType !== "SINGLE")
                                    .map((candidate, index) => (
                                      <article
                                        className="duplicate-analysis-item"
                                        key={`${candidate.columns.join("-")}-${index}`}
                                      >
                                        <strong>{candidate.columns.join(" + ")}</strong>

                                        <span>Combinación compuesta</span>

                                        <div className="duplicate-analysis-metadata">
                                          <span>
                                            Unicidad:{" "}
                                            {formatAnalysisValue(candidate.uniquenessPercentage)}%
                                          </span>
                                          <span>
                                            Filas evaluadas:{" "}
                                            {candidate.evaluatedRowCount.toLocaleString("es-CO")}
                                          </span>
                                          <span>
                                            Valores únicos:{" "}
                                            {candidate.uniqueCount.toLocaleString("es-CO")}
                                          </span>
                                        </div>
                                      </article>
                                    ))}
                                </div>
                              </details>
                            )}
                          </div>
                        )}

                      <p className="duplicate-analysis-summary">
                        Las claves candidatas y combinaciones únicas describen la
                        unicidad estadística de los datos evaluados. Esto no garantiza
                        que representen identificadores válidos o relevantes para el negocio.
                      </p>
                      </article>

                      <article className="duplicate-analysis-card">
                        <div className="duplicate-analysis-heading">
                          <h3>Valores repetidos por columna</h3>
                          <span>
                            {analysisResult.duplicateAnalysis.duplicateGroups.length.toLocaleString(
                              "es-CO",
                            )}
                          </span>
                        </div>

                        {analysisResult.duplicateAnalysis.duplicateGroups.length === 0 ? (
                          <p className="notice notice-info" role="status">
                            No se encontraron valores repetidos en columnas individuales.
                          </p>
                        ) : (
                          <div className="duplicate-analysis-list">
                            {analysisResult.duplicateAnalysis.duplicateGroups.map(
                              (group, index) => (
                                <details
                                  className="duplicate-analysis-item"
                                  key={`${group.columns.join("-")}-${index}`}
                                >
                                  <summary>
                                    <strong>{group.columns.join(" + ")}</strong>
                                    <span>
                                      {group.duplicateCount.toLocaleString("es-CO")}{" "}
                                      apariciones adicionales
                                    </span>
                                  </summary>

                                  <div className="duplicate-analysis-metadata">
                                    <span>
                                      Grupos:{" "}
                                      {group.groupCount.toLocaleString("es-CO")}
                                    </span>
                                    <span>
                                      Máxima frecuencia:{" "}
                                      {group.maxFrequency.toLocaleString("es-CO")}
                                    </span>
                                  </div>

                                  {group.examples.length > 0 && (
                                    <div className="duplicate-analysis-examples">
                                      <strong>Ejemplos de valores repetidos</strong>

                                      {group.examples.map((example, exampleIndex) => (
                                        <div
                                          className="duplicate-analysis-example"
                                          key={exampleIndex}
                                        >
                                          <span>
                                            {example.values
                                              .map((value) => formatAnalysisValue(value))
                                              .join(" · ")}
                                          </span>
                                          <span>
                                            {example.frequency.toLocaleString("es-CO")}{" "}
                                            apariciones
                                          </span>
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </details>
                              ),
                            )}
                          </div>
                        )}
                      </article>
                    </div>

                    <p className="duplicate-analysis-summary">
                      Combinaciones evaluadas:{" "}
                      <strong>
                        {analysisResult.duplicateAnalysis.evaluatedCombinations.toLocaleString(
                          "es-CO",
                        )}
                      </strong>
                    </p>

                    {analysisResult.duplicateAnalysis.columnsTruncated && (
                      <p className="notice notice-info" role="status">
                        El análisis de claves candidatas evaluó un máximo de 12 columnas.
                        Algunas columnas fueron excluidas.
                      </p>
                    )}

                    {analysisResult.duplicateAnalysis.combinationsTruncated && (
                      <p className="notice notice-info" role="status">
                        Se alcanzó el límite de combinaciones evaluadas.
                        Los resultados pueden no incluir todas las combinaciones posibles.
                      </p>
                    )}

                    {analysisResult.duplicateAnalysis.candidateKeys.filter(
                      (candidate) => candidate.keyType !== "SINGLE",
                    ).length > 10 && (
                      <p className="notice notice-info" role="status">
                        Se muestran las primeras 10 combinaciones únicas de{" "}
                        {analysisResult.duplicateAnalysis.candidateKeys.filter(
                          (candidate) => candidate.keyType !== "SINGLE",
                        ).length.toLocaleString("es-CO")}{" "}
                        detectadas. Las demás se omitieron únicamente de la presentación.
                      </p>
                    )}

                    {analysisResult.duplicateAnalysis.duplicateGroupsTruncated && (
                      <p className="notice notice-info" role="status">
                        Se muestran como máximo 20 ejemplos de grupos de valores repetidos por
                        columna. El total de grupos detectados sigue estando disponible.
                      </p>
                    )}
                  </div>

                  <div className="files-section">
                    <div className="section-heading">
                      <div>
                        <h2>Calidad de los datos</h2>
                        <p>Incidencias identificadas durante el análisis.</p>
                      </div>
                    </div>

                    {analysisResult.qualityIssues.length === 0 ? (
                      <p className="notice notice-info" role="status">
                        No se identificaron incidencias de calidad para las
                        reglas evaluadas.
                      </p>
                    ) : (
                      analysisResult.qualityIssues.map((issue, index) => (
                        <article
                          className="stat-card"
                          key={`${issue.columnName}-${issue.issueType}-${index}`}
                        >
                          <div className="stat-top">
                            <strong>{issue.columnName || "Archivo"}</strong>
                            <span>{issue.severity}</span>
                          </div>
                          <p>{issue.message}</p>
                          <span>{issue.issueType}</span>
                        </article>
                      ))
                    )}
                  </div>
                </>
              )}
            </section>
          )}

          <footer className="page-footer">
            <span>
              DATAFLOW <span className="footer-separator">·</span> INSIGHT
              STARTS HERE
            </span>
            <span>Construido para entender tus datos.</span>
          </footer>
        </div>
      </main>

      {showLogoutDialog && (
        <div
          className="dialog-overlay"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowLogoutDialog(false);
            }
          }}
        >
          <section
            className="logout-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="logout-dialog-title"
            aria-describedby="logout-dialog-description"
          >
            <div className="logout-dialog-icon" aria-hidden="true">
              ↪
            </div>

            <h2 id="logout-dialog-title">¿Cerrar sesión?</h2>

            <p id="logout-dialog-description">
              Tendrás que iniciar sesión nuevamente para acceder a tus archivos
              y análisis.
            </p>

            <div className="logout-dialog-actions">
              <button
                className="logout-cancel-button"
                type="button"
                onClick={() => setShowLogoutDialog(false)}
                ref={logoutCancelRef}
              >
                Cancelar
              </button>

              <button
                className="logout-confirm-button"
                type="button"
                aria-label="Confirmar cierre de sesión"
                onClick={() => {
                  setShowLogoutDialog(false);
                  handleLogout();
                }}
              >
                Cerrar sesión
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

export default App;
