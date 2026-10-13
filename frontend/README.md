# DataFlow — Frontend

Interfaz web de DataFlow, una aplicación orientada a facilitar la exploración y el análisis de calidad de archivos de datos.

## Tecnologías

- React 19
- TypeScript
- Vite
- ESLint

## Requisitos

- Node.js compatible con la versión de Vite instalada.
- npm.

## Instalación

Desde el directorio `frontend/`:

```bash
npm install
```

## Desarrollo

Desde el directorio `frontend/`:

```bash
npm run dev
```

Vite mostrará en la terminal la dirección local para abrir la aplicación en el navegador.

## Verificaciones

Comprobar los tipos de TypeScript y generar la compilación de producción:

```bash
npm run build
```

Ejecutar ESLint:

```bash
npm run lint
```

Previsualizar la compilación de producción:

```bash
npm run preview
```

## Funcionalidades actuales

- Dashboard con navegación entre resumen y archivos.
- Selección de archivos CSV y XLSX mediante explorador o arrastrar y soltar.
- Validación local de la extensión y del límite de 10 MB.
- Visualización del archivo seleccionado y opción para quitarlo.
- Tabla y tarjetas con datos ilustrativos.

## Estado de integración

La interfaz es actualmente un prototipo funcional a nivel visual e interactivo. La selección de archivos y sus validaciones ocurren en el navegador, pero la carga todavía no se envía al backend.

Los archivos de la tabla y las estadísticas son ejemplos visuales; no representan información consultada de una cuenta real ni resultados obtenidos de la base de datos.

La integración con la autenticación, la API de Spring Boot y los resultados reales del análisis está pendiente.

## Estructura principal

```text
frontend/
├── public/
├── src/
│   ├── App.tsx
│   ├── App.css
│   ├── index.css
│   └── main.tsx
├── index.html
├── package.json
├── package-lock.json
└── vite.config.ts
```
