import type { DistributionAnalysis as DistributionAnalysisData } from "../types/api";

interface DistributionAnalysisProps {
  analysis: DistributionAnalysisData;
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return "—";

  if (typeof value === "number") {
    return Number.isFinite(value)
      ? value.toLocaleString("es-CO", { maximumFractionDigits: 2 })
      : "—";
  }

  if (typeof value === "string") return value.trim() || "—";
  if (typeof value === "boolean") return value ? "Sí" : "No";

  return "—";
}

function DistributionAnalysis({ analysis }: DistributionAnalysisProps) {
  if (analysis.columns.length === 0) {
    return (
      <section className="files-section" aria-labelledby="distribution-title">
        <div className="section-heading">
          <div>
            <h2 id="distribution-title">Distribución de datos</h2>
            <p>Frecuencia de los valores en cada columna.</p>
          </div>
        </div>
        <p className="notice notice-info" role="status">
          No hay distribuciones disponibles para este análisis.
        </p>
      </section>
    );
  }

  return (
    <section className="files-section" aria-labelledby="distribution-title">
      <div className="section-heading">
        <div>
          <h2 id="distribution-title">Distribución de datos</h2>
          <p>
            Frecuencia de valores numéricos y categóricos en cada columna.
          </p>
        </div>
      </div>

      <div className="distribution-grid">
        {analysis.columns.map((column) => {
            const isNumeric = column.distributionType === "numeric";
            const isCategorical = column.distributionType === "categorical";
            const isSupported = isNumeric || isCategorical;

            const items = isNumeric
            ? column.bins
            : isCategorical
                ? column.categories
                : [];

            const maxCount = Math.max(...items.map((item) => item.count), 0);

          return (
            <article className="distribution-card" key={column.column}>
              <div className="duplicate-analysis-heading">
                <h3>{column.column}</h3>
                    <span>
                    {isNumeric ? "Numérica" : isCategorical ? "Categórica" : "No compatible"}
                    </span>
              </div>

              <div className="distribution-metadata">
                <span>
                  Válidos: {column.validCount.toLocaleString("es-CO")}
                </span>
                <span>
                  Faltantes: {column.nullCount.toLocaleString("es-CO")}
                </span>
              </div>
                {!isSupported ? (
                <p className="notice notice-info" role="status">
                    Este tipo de distribución todavía no es compatible con la visualización.
                </p>
                ) : items.length === 0 ? (
                <p className="notice notice-info" role="status">
                  No hay valores suficientes para construir esta distribución.
                </p>
              ) : (
                <div className="distribution-list">
                  {items.map((item, index) => {
                    const label = isNumeric
                      ? `${formatValue(column.bins[index].lowerBound)} – ${formatValue(column.bins[index].upperBound)}`
                      : formatValue(column.categories[index].value);

                    const width =
                      maxCount > 0 ? (item.count / maxCount) * 100 : 0;

                    return (
                      <div
                        className="distribution-item"
                        key={`${column.column}-${index}`}
                      >
                        <div className="distribution-item-heading">
                          <span title={label}>{label}</span>
                          <strong>
                            {item.count.toLocaleString("es-CO")}
                          </strong>
                        </div>

                        <div
                          className="distribution-bar-track"
                          role="img"
                          aria-label={`${label}: ${item.count} valores, ${formatValue(item.percentage)} por ciento`}
                        >
                          <div
                            className="distribution-bar"
                            style={{ width: `${width}%` }}
                          />
                        </div>

                        <span className="distribution-percentage">
                          {formatValue(item.percentage)}%
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}

              {column.categoriesTruncated && (
                <p className="notice notice-info" role="status">
                  Se muestran solo las categorías incluidas en el análisis.
                </p>
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}

export default DistributionAnalysis;