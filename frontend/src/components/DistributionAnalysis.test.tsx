import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import DistributionAnalysis from "./DistributionAnalysis";
import type { DistributionAnalysis as DistributionAnalysisData } from "../types/api";

describe("DistributionAnalysis", () => {
  it("muestra la distribución de una columna numérica", () => {
    const analysis: DistributionAnalysisData = {
      columns: [
        {
          column: "edad",
          distributionType: "numeric",
          validCount: 4,
          nullCount: 1,
          binCount: 2,
          bins: [
            {
              lowerBound: 20,
              upperBound: 30,
              count: 3,
              percentage: 75,
            },
            {
              lowerBound: 30,
              upperBound: 40,
              count: 1,
              percentage: 25,
            },
          ],
          categoryCount: null,
          categories: [],
          categoriesTruncated: false,
        },
      ],
    };

    render(<DistributionAnalysis analysis={analysis} />);

    expect(
      screen.getByRole("heading", { name: "Distribución de datos" }),
    ).toBeInTheDocument();

    expect(screen.getByRole("heading", { name: "edad" })).toBeInTheDocument();
    expect(screen.getByText("Numérica")).toBeInTheDocument();
    expect(screen.getByText("Válidos: 4")).toBeInTheDocument();
    expect(screen.getByText("Faltantes: 1")).toBeInTheDocument();
    expect(screen.getByText("20 – 30")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("30 – 40")).toBeInTheDocument();
    expect(screen.getByText("25%")).toBeInTheDocument();
  });

  it("muestra la distribución de una columna categórica", () => {
    const analysis: DistributionAnalysisData = {
      columns: [
        {
          column: "ciudad",
          distributionType: "categorical",
          validCount: 4,
          nullCount: 0,
          binCount: null,
          bins: [],
          categoryCount: 2,
          categories: [
            { value: "Bogotá", count: 3, percentage: 75 },
            { value: "Medellín", count: 1, percentage: 25 },
          ],
          categoriesTruncated: false,
        },
      ],
    };

    render(<DistributionAnalysis analysis={analysis} />);

    expect(screen.getByText("Categórica")).toBeInTheDocument();
    expect(screen.getByText("Bogotá")).toBeInTheDocument();
    expect(screen.getByText("Medellín")).toBeInTheDocument();
    expect(screen.getByText("75%")).toBeInTheDocument();
    expect(screen.getByText("25%")).toBeInTheDocument();
  });

  it("muestra un estado vacío cuando no hay distribuciones", () => {
    render(
      <DistributionAnalysis
        analysis={{ columns: [] }}
      />,
    );

    expect(
      screen.getByText(
        "No hay distribuciones disponibles para este análisis.",
      ),
    ).toBeInTheDocument();
  });

  it("muestra estados vacíos y advierte cuando las categorías están truncadas", () => {
    const analysis: DistributionAnalysisData = {
      columns: [
        {
          column: "producto",
          distributionType: "categorical",
          validCount: 3,
          nullCount: 0,
          binCount: null,
          bins: [],
          categoryCount: 3,
          categories: [],
          categoriesTruncated: true,
        },
      ],
    };

    render(<DistributionAnalysis analysis={analysis} />);

    const card = screen.getByRole("heading", { name: "producto" })
      .closest("article");

    expect(card).not.toBeNull();

    expect(
      within(card as HTMLElement).getByText(
        "No hay valores suficientes para construir esta distribución.",
      ),
    ).toBeInTheDocument();

    expect(
      within(card as HTMLElement).getByText(
        "Se muestran solo las categorías incluidas en el análisis.",
      ),
    ).toBeInTheDocument();
  });
    it("muestra un mensaje cuando el tipo de distribución no es compatible", () => {
    const analysis: DistributionAnalysisData = {
      columns: [
        {
          column: "datos",
          distributionType: "unknown",
          validCount: 2,
          nullCount: 0,
          binCount: null,
          bins: [],
          categoryCount: null,
          categories: [
            { value: "A", count: 2, percentage: 100 },
          ],
          categoriesTruncated: false,
        },
      ],
    };

    render(<DistributionAnalysis analysis={analysis} />);

    expect(screen.getByText("No compatible")).toBeInTheDocument();

    expect(
      screen.getByText(
        "Este tipo de distribución todavía no es compatible con la visualización.",
      ),
    ).toBeInTheDocument();

    expect(screen.queryByText("A")).not.toBeInTheDocument();
  });
});