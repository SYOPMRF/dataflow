# Arquitectura de DataFlow

## 1. Descripción general

DataFlow es una aplicación web para cargar archivos CSV y XLSX, analizar su estructura y obtener métricas estadísticas e indicadores básicos de calidad de datos.

La arquitectura está organizada en componentes con responsabilidades separadas:

- **Frontend:** interfaz de usuario y comunicación con la API.
- **Backend:** autenticación, autorización, gestión de archivos, coordinación del procesamiento y persistencia de resultados.
- **Data Service:** lectura, análisis estadístico y detección de problemas de calidad.
- **PostgreSQL:** almacenamiento de usuarios, archivos, estados y resultados de análisis.
- **Docker Compose:** ejecución coordinada de los servicios y sus dependencias.

---

## 2. Componentes y responsabilidades

### 2.1. Frontend

**Tecnologías:** React, TypeScript, Vite y Nginx.

**Ubicación:** `frontend/`

Responsabilidades:

- Proporcionar la interfaz de usuario.
- Gestionar las pantallas de autenticación.
- Consumir los endpoints REST del backend.
- Enviar archivos para su almacenamiento y procesamiento.
- Consultar la información de los archivos y sus resultados de análisis.

En producción, Nginx sirve los archivos estáticos del frontend y actúa como proxy inverso para las solicitudes que comienzan por `/api/`, redirigiéndolas al backend.

### 2.2. Backend

**Tecnologías:** Java 21, Spring Boot, Spring Security, Spring Data JPA y Maven.

**Ubicación:** `backend/`

El backend es el componente central de la aplicación y se encarga de coordinar las operaciones.

Sus principales responsabilidades son:

- Registrar usuarios e iniciar sesiones mediante autenticación JWT.
- Aplicar autorización a los endpoints protegidos.
- Gestionar los archivos asociados a cada usuario.
- Validar y almacenar archivos CSV y XLSX.
- Coordinar el procesamiento mediante el Data Service.
- Controlar los estados de procesamiento.
- Persistir los resultados y las métricas.
- Gestionar la eliminación de archivos y la recuperación de procesos pendientes.

El código está organizado en paquetes que separan controladores, servicios, entidades, repositorios, DTO, seguridad, validaciones, excepciones y migraciones.

#### Seguridad

Spring Security utiliza:

- **JWT:** autenticación sin sesiones de servidor.
- **BCrypt:** hash de contraseñas.
- **Filtro de autenticación JWT:** validación de los tokens recibidos.
- **Autorización:** protección de los endpoints de la aplicación.

Las rutas de autenticación, los endpoints de salud y la comprobación de disponibilidad del Data Service están permitidos sin autenticación. Los demás endpoints requieren autenticación.

#### Almacenamiento de archivos

Los archivos admitidos son CSV y XLSX, con un tamaño máximo de 10 MB.

El almacenamiento utiliza nombres generados mediante UUID para evitar depender del nombre original y reducir conflictos entre archivos.

Los metadatos se registran en PostgreSQL y el contenido se almacena en el sistema de archivos, utilizando un volumen persistente en Docker.

#### Procesamiento y estados

El procesamiento se coordina mediante `FileAnalysisService`.

Los estados utilizados son:

| Estado       | Descripción                                      |
| ------------ | ------------------------------------------------ |
| `UPLOADED`   | Archivo almacenado y pendiente de procesamiento. |
| `PROCESSING` | Procesamiento en curso.                          |
| `COMPLETED`  | Análisis completado y resultados persistidos.    |
| `FAILED`     | Procesamiento fallido.                           |

El backend utiliza una operación condicional en la base de datos para reclamar el procesamiento de un archivo, evitando que dos solicitudes lo procesen simultáneamente.

Los archivos en estado `UPLOADED` o `FAILED` pueden iniciar un nuevo procesamiento. Los archivos `PROCESSING` y `COMPLETED` no pueden iniciar otro procesamiento mientras mantengan esos estados.

#### Persistencia de resultados

`AnalysisPersistenceService` guarda los resultados dentro de una transacción.

La información se distribuye en las siguientes entidades principales:

- `AnalysisResultEntity`: métricas generales del archivo.
- `FileColumn`: métricas estadísticas de cada columna.
- `DataQualityIssueEntity`: problemas de calidad detectados.

Cuando la persistencia termina correctamente, el archivo pasa a `COMPLETED`.

### 2.3. Data Service

**Tecnologías:** Python, FastAPI, Pandas y Pydantic.

**Ubicación:** `data-service/`

El Data Service es un componente independiente encargado exclusivamente del análisis de datos.

Sus responsabilidades son:

- Recibir archivos mediante solicitudes HTTP multipart.
- Leer archivos CSV y XLSX.
- Convertir su contenido en DataFrames de Pandas.
- Calcular métricas generales y estadísticas por columna.
- Detectar problemas básicos de calidad.
- Devolver los resultados en formato JSON validado mediante modelos Pydantic.

El servicio expone los siguientes endpoints:

| Método | Endpoint    | Propósito                       |
| ------ | ----------- | ------------------------------- |
| `GET`  | `/health`   | Comprobar disponibilidad.       |
| `POST` | `/analysis` | Analizar un archivo CSV o XLSX. |

#### Análisis estadístico

Para cada archivo se calculan:

- Cantidad de filas y columnas.
- Cantidad total de valores faltantes.
- Cantidad de filas duplicadas.

Para cada columna se calculan:

- Tipo de dato.
- Cantidad de valores nulos.
- Cantidad y porcentaje de valores únicos.
- Cantidad de valores duplicados.
- Porcentaje de valores faltantes.
- Indicador de columna completamente vacía.
- Valor mínimo y máximo.
- Media, mediana y desviación estándar para columnas numéricas.
- Cantidad de ceros y valores negativos para columnas numéricas.

#### Detección de calidad

`QualityService` identifica los siguientes problemas:

| Problema                                                 | Severidad |
| -------------------------------------------------------- | --------- |
| Columna completamente vacía                              | `HIGH`    |
| Porcentaje de valores faltantes igual o superior al 50 % | `HIGH`    |
| Presencia de valores faltantes por debajo del 50 %       | `MEDIUM`  |
| Presencia de valores negativos                           | `MEDIUM`  |

Estas reglas representan las validaciones implementadas actualmente; no constituyen una evaluación exhaustiva de todas las dimensiones de calidad de datos.

### 2.4. PostgreSQL

**Tecnología:** PostgreSQL 18.

PostgreSQL almacena la información estructurada de la aplicación.

Entre sus responsabilidades se encuentran:

- Persistir usuarios.
- Registrar metadatos de archivos.
- Mantener los estados de procesamiento.
- Guardar resultados generales de análisis.
- Guardar métricas por columna.
- Registrar problemas de calidad.
- Mantener información de eliminaciones pendientes.

Las migraciones del esquema se ejecutan mediante el servicio `migrate` de Docker Compose. El backend utiliza un usuario de base de datos con privilegios limitados para las operaciones normales de la aplicación.

---

## 3. Comunicación entre componentes

La comunicación sigue este esquema:

1. El navegador realiza solicitudes al frontend.
2. Nginx sirve la aplicación React y redirige las solicitudes `/api/` al backend.
3. Spring Boot autentica y autoriza las solicitudes protegidas.
4. El backend gestiona los archivos y consulta PostgreSQL cuando corresponde.
5. Para analizar un archivo, Spring Boot lee su contenido y lo envía al Data Service mediante HTTP multipart.
6. FastAPI procesa el archivo y devuelve un resultado JSON.
7. Spring Boot persiste el resultado en PostgreSQL.
8. El frontend consulta el backend para recuperar los resultados.

**Importante:** el frontend no se comunica directamente con el Data Service ni con PostgreSQL.

---

## 4. Flujo de procesamiento de archivos

El procesamiento de un archivo sigue estas etapas:

1. **Carga:** el usuario envía un archivo CSV o XLSX desde el frontend.
2. **Validación:** Spring Boot comprueba que el archivo no esté vacío, que su extensión esté permitida y que no supere los 10 MB.
3. **Almacenamiento:** el backend guarda el contenido con un nombre UUID.
4. **Registro:** se guardan los metadatos del archivo y se establece el estado `UPLOADED`.
5. **Inicio del procesamiento:** el backend verifica la propiedad del archivo e intenta cambiar su estado a `PROCESSING` mediante una operación condicional.
6. **Comunicación:** Spring Boot envía el contenido del archivo a FastAPI.
7. **Análisis:** Pandas calcula las métricas y `QualityService` detecta los problemas de calidad.
8. **Respuesta:** FastAPI devuelve los resultados en JSON.
9. **Persistencia:** Spring Boot guarda las métricas generales, las métricas por columna y los problemas de calidad dentro de una transacción.
10. **Finalización:** el archivo pasa a `COMPLETED`.
11. **Consulta:** el frontend solicita los resultados al backend.

Si ocurre un error durante el procesamiento o la persistencia, el backend intenta marcar el archivo como `FAILED`, permitiendo un nuevo intento posteriormente.

---

## 5. Eliminación y recuperación

DataFlow incorpora mecanismos para manejar situaciones en las que una operación no termina como se esperaba.

### 5.1. Eliminación de archivos

La eliminación de un archivo contempla tanto sus registros asociados como su contenido físico.

El backend registra las eliminaciones pendientes y coordina la limpieza del archivo físico después de confirmar la transacción de base de datos.

Un servicio programado procesa periódicamente las eliminaciones pendientes que no pudieron completarse.

### 5.2. Recuperación de procesamientos interrumpidos

El backend dispone de un mecanismo de recuperación para archivos que permanecen en estado `PROCESSING` después de una interrupción.

Este mecanismo permite detectar y recuperar archivos cuyo procesamiento quedó incompleto, evitando que permanezcan bloqueados indefinidamente.

---

## 6. Infraestructura local con Docker Compose

La configuración principal de Docker Compose define cinco servicios:

| Servicio       | Responsabilidad                                                                   |
| -------------- | --------------------------------------------------------------------------------- |
| `frontend`     | Servir React mediante Nginx y redirigir las solicitudes API.                      |
| `backend`      | Ejecutar la API Spring Boot.                                                      |
| `data-service` | Ejecutar el servicio de análisis FastAPI.                                         |
| `db`           | Ejecutar PostgreSQL.                                                              |
| `migrate`      | Ejecutar las migraciones y aprovisionar el usuario de ejecución de la aplicación. |

Los servicios se comunican mediante una red interna de Docker.

### Volúmenes persistentes

La infraestructura utiliza los siguientes volúmenes:

| Volumen         | Descripción                                      |
| --------------- | ------------------------------------------------ |
| `postgres_data` | Almacena los datos persistentes de PostgreSQL.   |
| `uploads_data`  | Almacena los archivos cargados por los usuarios. |

### Puertos publicados

Los puertos publicados se limitan a la interfaz local del host:

| Servicio   | Dirección local         |
| ---------- | ----------------------- |
| Frontend   | `http://localhost:3000` |
| Backend    | `http://localhost:8080` |
| PostgreSQL | `localhost:5433`        |

FastAPI no publica un puerto directamente al host en la configuración principal; se comunica con el backend mediante la red interna de Docker.

---

## 7. Consideraciones de diseño

La arquitectura busca mantener responsabilidades separadas y facilitar el mantenimiento:

- **Separación de responsabilidades:** Spring Boot gestiona la lógica de aplicación y Python se concentra en el análisis de datos.
- **Persistencia centralizada:** el backend controla las operaciones sobre PostgreSQL.
- **Seguridad por usuario:** los archivos y sus resultados se consultan respetando la propiedad del recurso.
- **Procesamiento controlado:** los cambios de estado y la reclamación condicional evitan ejecuciones simultáneas no permitidas.
- **Persistencia transaccional:** los resultados de análisis se guardan de forma atómica.
- **Despliegue reproducible:** Docker Compose permite ejecutar los componentes con sus dependencias.
- **Privilegios mínimos:** el backend utiliza un usuario de base de datos con permisos limitados.

---

## 8. Evoluciones futuras

Las siguientes tecnologías y capacidades se consideran posibles evoluciones, no funcionalidades incluidas en la implementación actual:

- Despliegue en AWS.
- Infraestructura como código mediante Terraform.
- Almacenamiento de archivos en servicios cloud como Amazon S3.
- Monitoreo avanzado con Prometheus y Grafana.
- Orquestación con Kubernetes, si las necesidades del proyecto lo justifican.

---
