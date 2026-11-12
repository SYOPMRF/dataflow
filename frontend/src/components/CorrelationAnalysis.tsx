import type { CorrelationAnalysis as CorrelationAnalysisData } from "../types/api";

interface CorrelationAnalysisProps {
  analysis: CorrelationAnalysisData;
}

interface CorrelationMatrixProps {
  title: string;
  description: string;
  columns: string[];
  values: Array<Array<number | null>>;
  observationCounts: number[][];
}

function formatCoefficient(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return "—";
  }

  return value.toLocaleString("es-CO", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

function getCorrelationClass(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return "correlation-cell-unavailable";
  }

  const absoluteValue = Math.abs(value);

  if (absoluteValue < 0.3) return "correlation-cell-weak";
  if (value > 0) {
    return absoluteValue >= 0.7
      ? "correlation-cell-strong-positive"
      : "correlation-cell-moderate-positive";
  }

  return absoluteValue >= 0.7
    ? "correlation-cell-strong-negative"
    : "correlation-cell-moderate-negative";
}

function CorrelationMatrix({
  title,
  description,
  columns,
  values,
  observationCounts,
}: CorrelationMatrixProps) {
  return (
    <article className="correlation-card">
      <div className="duplicate-analysis-heading">
        <h3>{title}</h3>
        <span>{columns.length} variables</span>
      </div>

      <p className="correlation-description">{description}</p>

      <div className="correlation-table-wrapper">
        <table className="correlation-table">
          <thead>
            <tr>
              <th scope="col">Variable</th>
              {columns.map((column) => (
                <th scope="col" key={column}>
                  {column}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {columns.map((rowColumn, rowIndex) => (
              <tr key={rowColumn}>
                <th scope="row">{rowColumn}</th>
                {columns.map((column, columnIndex) => {
                  const value = values[rowIndex]?.[columnIndex] ?? null;
                  const observations =
                    observationCounts[rowIndex]?.[columnIndex];

                  return (
                    <td
                      className={getCorrelationClass(value)}
                      key={`${rowColumn}-${column}`}
                      title={`${rowColumn} / ${column}: ${formatCoefficient(value)}`}
                    >
                      <strong>{formatCoefficient(value)}</strong>
                      <span>
                        n={observations?.toLocaleString("es-CO") ?? "—"}
                      </span>
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </article>
  );
}

function CorrelationAnalysis({ analysis }: CorrelationAnalysisProps) {
  if (analysis.columns.length === 0) {
    return (
      <section className="files-section" aria-labelledby="correlation-title">
        <div className="section-heading">
          <div>
            <h2 id="correlation-title">Correlaciones</h2>
            <p>Relaciones estadísticas entre variables numéricas.</p>
          </div>
        </div>

        <p className="notice notice-info" role="status">
          No hay suficientes variables numéricas para calcular correlaciones.
        </p>
      </section>
    );
  }

  return (
    <section className="files-section" aria-labelledby="correlation-title">
      <div className="section-heading">
        <div>
          <h2 id="correlation-title">Correlaciones</h2>
          <p>
            Relaciones estadísticas entre variables numéricas mediante
            Pearson y Spearman. Los coeficientes van de -1 a 1.
          </p>
        </div>
      </div>

      <div className="correlation-grid">
        <CorrelationMatrix
          title="Pearson"
          description="Mide la fuerza y dirección de la relación lineal."
          columns={analysis.columns}
          values={analysis.pearson}
          observationCounts={analysis.observationCounts}
        />

        <CorrelationMatrix
          title="Spearman"
          description="Mide la relación monotónica mediante rangos."
          columns={analysis.columns}
          values={analysis.spearman}
          observationCounts={analysis.observationCounts}
        />
      </div>

      <div className="correlation-legend" aria-label="Leyenda de correlaciones">
        <span className="correlation-legend-item">
          <i className="correlation-cell-strong-positive" />
          Positiva fuerte
        </span>
        <span className="correlation-legend-item">
          <i className="correlation-cell-moderate-positive" />
          Positiva moderada
        </span>
        <span className="correlation-legend-item">
          <i className="correlation-cell-weak" />
          Débil o nula
        </span>
        <span className="correlation-legend-item">
          <i className="correlation-cell-moderate-negative" />
          Negativa moderada
        </span>
        <span className="correlation-legend-item">
          <i className="correlation-cell-strong-negative" />
          Negativa fuerte
        </span>
      </div>

      {analysis.truncated && (
        <p className="notice notice-info" role="status">
          El análisis de correlaciones fue limitado. No se evaluaron todas las
          variables posibles.
        </p>
      )}
    </section>
  );
}

export default CorrelationAnalysis;
