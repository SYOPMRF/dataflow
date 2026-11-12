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

## 3. Modalidades de almacenamiento

DataFlow permite ejecutar la demostración con dos proveedores de almacenamiento:

- **LOCAL:** utiliza el sistema de archivos del backend y el volumen persistente `uploads_data`. Es la configuración predeterminada.
- **S3:** utiliza PGSTY Silo como almacenamiento de objetos compatible con Amazon S3. Se activa mediante el overlay `docker-compose.minio.yml`.

### Demostración con almacenamiento LOCAL

Desde la raíz del proyecto, ejecutar:

```bash
docker compose up --build -d
```

### Demostración con almacenamiento S3-compatible

Antes de iniciar esta modalidad, configurar en `.env` las credenciales administrativas de Silo y las credenciales específicas del usuario de aplicación.

**1. Iniciar únicamente el servicio Silo:**

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml up -d silo
```

**2. Configurar el almacenamiento desde la consola web:**

Acceder a `http://localhost:9001` y:

- Crear el bucket privado `dataflow-files`.
- Crear el usuario de aplicación.
- Asignarle una política con permisos limitados sobre ese bucket: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` y `s3:ListBucket`.

**3. Iniciar el resto del entorno con ambos archivos Compose:**

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml up --build -d
```

El backend utiliza las credenciales del usuario de aplicación, no las credenciales administrativas de Silo.

La API S3 está disponible dentro de Docker en `http://silo:9000`. La consola web se publica localmente en el puerto `9001`.

## 4. Iniciar y comprobar la aplicación

Después de seleccionar la modalidad de almacenamiento de la sección anterior, comprobar que los servicios estén iniciados.

Para la modalidad LOCAL:

```bash
docker compose ps
```

Para la modalidad S3-compatible:

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml ps
```

### Servicios principales

| Servicio   | Dirección local         |
| ---------- | ----------------------- |
| Frontend   | `http://localhost:3000` |
| Backend    | `http://localhost:8080` |
| PostgreSQL | `localhost:5433`        |

En ambas modalidades, FastAPI se comunica internamente con el backend mediante la red de Docker.

### Comprobación de disponibilidad

Para comprobar la disponibilidad del backend:

```bash
curl http://localhost:8080/actuator/health
```

Para comprobar la disponibilidad del Data Service a través del backend:

```bash
curl http://localhost:8080/api/data-service/health
```

## 5. Archivo de demostración

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

## 6. Recorrido de demostración

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

| Métrica | Resultado esperado |
| --- | ---: |
| Filas | 4 |
| Columnas | 3 |
| Valores faltantes | 0 |
| Filas duplicadas | 1 |

Después, desplegar las estadísticas de cada columna para mostrar:

- Tipo de dato y valores ausentes.
- Cantidad y porcentaje de valores únicos y duplicados.
- Mínimo, máximo, media, mediana y desviación estándar, cuando corresponda.
- Cuartiles, rango intercuartílico y percentiles.
- Cantidad de ceros, valores negativos y valores atípicos detectados mediante IQR y Z-score.

Mostrar también las secciones de análisis avanzado:

**Duplicados y claves candidatas**

- Combinaciones de columnas evaluadas.
- Posibles claves candidatas, si se identifican.
- Ejemplos de valores repetidos y sus frecuencias.

Para este archivo, el análisis no identifica claves candidatas y presenta valores repetidos como `Alice`, `25` y `3000`, cada uno con frecuencia 2.

**Distribuciones**

- Histogramas para las columnas numéricas `age` y `salary`.
- Distribución categórica de `name`: Alice (2), Bob (1) y Charlie (1).

**Correlaciones**

- Pearson entre `age` y `salary`: 0.71, con 4 observaciones.
- Spearman entre `age` y `salary`: 0.78, con 4 observaciones.

Los coeficientes muestran una asociación positiva en este pequeño conjunto de ejemplo; no permiten establecer causalidad ni extraer conclusiones generales.

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

El proveedor de almacenamiento utilizado originalmente queda registrado para que la limpieza se realice en el destino correcto:

- Con `LOCAL`, se elimina el archivo del sistema de archivos del backend.
- Con `S3`, se elimina el objeto almacenado en el bucket de Silo.

Si la eliminación física falla, el sistema conserva la tarea pendiente en PostgreSQL y programa nuevos intentos.

### Paso 8. Cerrar sesión

Utilizar la opción **Cerrar sesión** y comprobar que se regresa a la pantalla de autenticación.

---

## 7. Aspectos técnicos que se pueden explicar

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

## 8. Limitaciones actuales

La demostración debe presentar únicamente funcionalidades implementadas.

Las siguientes limitaciones deben tenerse en cuenta:

- La tarjeta de calidad del resumen todavía no calcula una métrica agregada.
- La calidad se presenta mediante las incidencias detectadas durante el análisis individual.
- Las reglas implementadas cubren comprobaciones generales, formatos reconocidos y determinadas consistencias semánticas; no representan una evaluación exhaustiva de todas las dimensiones de calidad.
- Las validaciones de formato y semánticas dependen de los nombres de columnas reconocidos y de la información disponible en cada archivo.
- La detección de claves candidatas es una aproximación estadística sobre los datos analizados, no una garantía de integridad.
- Las correlaciones Pearson y Spearman describen asociaciones estadísticas y no permiten establecer causalidad.
- Algunas evaluaciones están acotadas para controlar su coste.
- El procesamiento se ejecuta mediante solicitudes al backend y al Data Service; no se presenta como un sistema distribuido de procesamiento por lotes.
- La infraestructura implementada y probada corresponde al entorno local con Docker Compose.
- El almacenamiento S3-compatible está implementado y probado localmente mediante PGSTY Silo.
- El despliegue en AWS y la orquestación con Kubernetes siguen siendo evoluciones futuras.
- La migración automática de archivos existentes entre proveedores de almacenamiento no está implementada.

---

## 9. Detener la aplicación

Al terminar la demostración, detener los servicios según la modalidad utilizada.

**Modalidad LOCAL:**

```bash
docker compose down
```

**Modalidad S3-compatible:**

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml down
```

Estos comandos detienen y eliminan los contenedores y las redes del entorno, pero conservan los volúmenes persistentes.

**Importante:** no utilizar `docker compose down -v`, porque eliminaría los volúmenes y los datos persistentes, incluidos los archivos almacenados localmente y los objetos de Silo.
