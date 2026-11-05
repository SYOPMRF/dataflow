# Guía de demostración de DataFlow

## 1. Objetivo

Esta guía describe cómo ejecutar DataFlow en un entorno local y realizar una demostración de sus funcionalidades principales.

La demostración permite recorrer el registro, la autenticación, la carga de archivos, el análisis estadístico y la consulta de resultados.

---

## 2. Requisitos previos

Antes de iniciar, es necesario contar con:

- Docker y Docker Compose instalados.
- El repositorio de DataFlow clonado localmente.
- Las variables de entorno configuradas siguiendo las instrucciones del README.
- Docker Engine en ejecución.

**Importante:** no se deben compartir credenciales, tokens ni secretos durante la demostración.

---

## 3. Iniciar la aplicación

Desde la raíz del proyecto, ejecutar:

```bash
docker compose up --build -d
```

Comprobar que los servicios estén iniciados:

```bash
docker compose ps
```

### Servicios principales

| Servicio   | Dirección                                      |
| ---------- | ---------------------------------------------- |
| Frontend   | [http://localhost:3000](http://localhost:3000) |
| Backend    | [http://localhost:8080](http://localhost:8080) |
| PostgreSQL | `localhost:5433`                               |

FastAPI se comunica internamente con el backend mediante la red de Docker.

### Comprobación de disponibilidad

Para comprobar la disponibilidad del backend:

```bash
curl http://localhost:8080/actuator/health
```

Para comprobar la disponibilidad del Data Service a través del backend:

```bash
curl http://localhost:8080/api/data-service/health
```

---

## 4. Archivo de demostración

Se propone utilizar el siguiente archivo de ejemplo:

**Ubicación:** `backend/test_data.csv`

### Contenido

```csv
name,age,salary
Alice,25,3000
Bob,30,3500
Alice,25,3000
Charlie,28,4000
```

### Características del archivo

Este archivo contiene:

- 4 filas.
- 3 columnas.
- 1 fila duplicada.
- Ningún valor faltante.
- Datos de texto y numéricos.
- Valores adecuados para demostrar estadísticas descriptivas.

Es un archivo pequeño, reproducible y apropiado para una demostración inicial.

---

## 5. Recorrido de demostración

### Paso 1. Registro

1. Abrir [http://localhost:3000](http://localhost:3000).
2. Seleccionar la opción de registro.
3. Introducir un correo electrónico de prueba y una contraseña.
4. Confirmar la creación de la cuenta.
5. Iniciar sesión con las credenciales registradas.

### Paso 2. Espacio de trabajo

Después de iniciar sesión, mostrar los siguientes elementos:

- El resumen de actividad.
- La cantidad de archivos cargados.
- La cantidad de análisis completados.
- La sección de carga de archivos.
- La navegación entre **Resumen** y **Mis archivos**.

### Paso 3. Carga del archivo

1. Seleccionar **Nuevo análisis** o **Explorar archivos**.
2. Buscar `backend/test_data.csv` en el repositorio local.
3. Seleccionar el archivo.
4. Comprobar que aparece como seleccionado.
5. Pulsar **Subir archivo**.
6. Verificar que aparece en la lista con estado **Pendiente**.

DataFlow admite archivos CSV y XLSX de hasta 10 MB.

### Paso 4. Procesamiento

1. Localizar el archivo cargado.
2. Pulsar **Analizar**.
3. Esperar a que termine el procesamiento.
4. Comprobar que el archivo aparece como **Completado**.

El backend coordina el procesamiento y envía el contenido al Data Service para su análisis.

### Paso 5. Consulta de resultados

Mostrar los resultados generales esperados para el archivo de ejemplo:

| Métrica           | Resultado esperado |
| ----------------- | -----------------: |
| Filas             |                  4 |
| Columnas          |                  3 |
| Valores faltantes |                  0 |
| Filas duplicadas  |                  1 |

Después, desplegar las estadísticas de cada columna para mostrar:

- Tipo de dato.
- Valores faltantes.
- Valores únicos.
- Media.
- Mínimo y máximo.
- Mediana.
- Desviación estándar.
- Valores cero y negativos.

Finalmente, mostrar la sección de calidad de los datos.

Para este archivo no se esperan incidencias bajo las reglas de calidad implementadas.

### Paso 6. Consultar nuevamente los resultados

1. Volver a **Mis archivos**.
2. Seleccionar **Ver resultados**.
3. Comprobar que los resultados persistidos pueden consultarse sin ejecutar nuevamente el análisis.

Este paso permite demostrar que los resultados se almacenan en PostgreSQL y pueden recuperarse posteriormente.

### Paso 7. Eliminar el archivo

1. Volver a **Mis archivos**.
2. Pulsar **Eliminar**.
3. Confirmar la operación.
4. Verificar que el archivo desaparece de la lista.

La eliminación también contempla los registros asociados y la limpieza del archivo físico mediante el mecanismo de eliminaciones pendientes.

### Paso 8. Cerrar sesión

Utilizar la opción **Cerrar sesión** y comprobar que se regresa a la pantalla de autenticación.

---

## 6. Aspectos técnicos que se pueden explicar

Durante la demostración se pueden describir brevemente las tecnologías y responsabilidades principales:

| Tecnología o componente | Función dentro de DataFlow                                                    |
| ----------------------- | ----------------------------------------------------------------------------- |
| React y TypeScript      | Interfaz de usuario y comunicación con la API.                                |
| Spring Boot             | Autenticación, autorización, gestión de archivos y coordinación del análisis. |
| FastAPI y Pandas        | Lectura y análisis estadístico de los archivos.                               |
| PostgreSQL              | Persistencia de usuarios, metadatos y resultados.                             |
| Docker Compose          | Ejecución coordinada de los servicios.                                        |
| JWT y BCrypt            | Autenticación y protección de credenciales.                                   |
| Transacciones y estados | Control del procesamiento y persistencia consistente de resultados.           |

---

## 7. Limitaciones actuales

La demostración debe presentar únicamente funcionalidades implementadas.

Las siguientes limitaciones deben tenerse en cuenta:

- La tarjeta de calidad del resumen todavía no calcula una métrica agregada.
- La calidad se presenta mediante las incidencias detectadas durante el análisis individual.
- Las reglas de calidad son básicas y no representan una evaluación exhaustiva.
- El procesamiento se ejecuta mediante solicitudes al backend y al Data Service; no se presenta como un sistema distribuido de procesamiento por lotes.
- La infraestructura documentada corresponde al entorno local con Docker Compose.
- AWS, Terraform y Kubernetes son evoluciones futuras y no forman parte de la implementación actual.

---

## 8. Detener la aplicación

Al terminar la demostración, desde la raíz del proyecto ejecutar:

```bash
docker compose down
```

Este comando detiene y elimina los contenedores y la red del entorno, pero conserva los volúmenes persistentes.

**Importante:** no utilizar `docker compose down -v`, porque eliminaría los volúmenes y los datos persistentes.

---
