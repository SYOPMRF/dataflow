import { useEffect, useRef, useState } from "react";
import AuthScreen from "./components/AuthScreen";
import { ApiError, api } from "./services/api";
import type { AnalysisResult, FileResponse, LoginResponse } from "./types/api";
import "./App.css";

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
      return { label: "Procesando", className: "status-pending" };
    case "FAILED":
      return { label: "Error", className: "status-error" };
    case "UPLOADED":
      return { label: "Pendiente", className: "status-pending" };
  }
}

const navigation = [
  { label: "Resumen", icon: "▦" },
  { label: "Mis archivos", icon: "▤" },
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
            <span>↗</span>
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
              <span className="nav-icon">{item.icon}</span>
              {item.label}
              {item.label === "Mis archivos" && (
                <span className="nav-count">{files.length}</span>
              )}
            </button>
          ))}
        </nav>

        <div className="sidebar-bottom">
          <div className="help-card">
            <div className="help-icon">✦</div>
            <strong>¿Primera vez aquí?</strong>
            <p>Sube tus datos y descubre qué contienen.</p>
            <button onClick={openFilePicker}>
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
              <span aria-hidden="true">↪</span>
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
              ♧
            </button>
            <div className="top-avatar">DF</div>
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-section">
            <div>
              <div className="eyebrow">
                <span className="eyebrow-dot" /> TU ESPACIO DE DATOS
              </div>
              <h1>
                {activePage === "Resumen"
                  ? "Tus datos, más claros."
                  : "Mis archivos."}
              </h1>
              <p className="welcome-description">
                {activePage === "Resumen"
                  ? "Sube un archivo y deja que el análisis comience."
                  : "Consulta tus archivos y prepara tus próximos análisis."}
              </p>
            </div>
            <button className="primary-button" onClick={openFilePicker}>
              <span className="button-plus">＋</span> Nuevo análisis
            </button>
          </section>

          <section className="stats-grid" aria-label="Resumen de actividad">
            <article className="stat-card">
              <div className="stat-top">
                <span>Archivos cargados</span>
                <span className="stat-icon purple">▤</span>
              </div>
              <div className="stat-value">
                {files.length.toString().padStart(2, "0")}
              </div>
              <div className="stat-caption">Tus datos en un solo lugar</div>
            </article>

            <article className="stat-card">
              <div className="stat-top">
                <span>Análisis completados</span>
                <span className="stat-icon green">✓</span>
              </div>
              <div className="stat-value">
                {files
                  .filter((file) => file.status === "COMPLETED")
                  .length.toString()
                  .padStart(2, "0")}
              </div>
              <div className="stat-caption">
                <span className="tiny-green-dot" /> Archivos listos para
                consultar
              </div>
            </article>

            <article className="stat-card">
              <div className="stat-top">
                <span>Calidad de los datos</span>
                <span className="stat-icon amber">◇</span>
              </div>
              <div className="stat-value">
                — <span className="stat-trend">sin calcular</span>
              </div>
              <div className="stat-caption">
                Se mostrará cuando definamos una métrica de calidad agregada
              </div>
            </article>
          </section>

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
            {notice && (
              <p
                className={`notice ${noticeType === "info" ? "notice-info" : "notice-error"}`}
                role={noticeType === "error" ? "alert" : "status"}
              >
                {notice}
              </p>
            )}
          </section>

          <section className="files-section">
            <div className="section-heading files-heading">
              <div>
                <h2>
                  {activePage === "Resumen"
                    ? "Archivos recientes"
                    : "Todos los archivos"}
                </h2>
                <p>Un vistazo rápido a tu actividad reciente.</p>
              </div>
              <button
                className="text-button"
                onClick={() =>
                  setActivePage(
                    activePage === "Resumen" ? "Mis archivos" : "Resumen",
                  )
                }
              >
                {activePage === "Resumen" ? "Ver todos" : "Volver al resumen"}{" "}
                <span>→</span>
              </button>
            </div>

            <div className="files-table">
              <div className="table-header">
                <span>ARCHIVO</span>
                <span>FECHA</span>
                <span>TAMAÑO</span>
                <span>ESTADO</span>
                <span />
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
                files.map((file) => {
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
                          </div>
                        </details>
                      ))}
                    </div>
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
