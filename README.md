# DataFlow

**DataFlow** es una aplicación web para cargar, procesar y analizar archivos CSV y Excel. Proporciona estadísticas descriptivas y comprobaciones básicas de calidad de datos, con gestión de archivos y resultados asociados a cada usuario.

El proyecto está construido como una arquitectura de servicios separados, con autenticación, persistencia, procesamiento de datos, contenedores y pruebas automatizadas.

---

## Funcionalidades

- Registro e inicio de sesión mediante autenticación JWT.
- Hash de contraseñas mediante BCrypt.
- Carga de archivos CSV y XLSX de hasta 10 MB.
- Consulta y eliminación de archivos propios.
- Procesamiento de archivos mediante un servicio independiente en Python.
- Estadísticas descriptivas generales y por columna.
- Detección de problemas básicos de calidad de datos.
- Persistencia de resultados de análisis en PostgreSQL.
- Control de acceso por usuario.
- Recuperación de archivos cuyo procesamiento quedó interrumpido.
- Gestión de eliminaciones pendientes.
- Ejecución de los servicios mediante Docker Compose.
- Integración continua con GitHub Actions.

---

## Arquitectura

DataFlow utiliza una arquitectura de servicios separados, donde cada componente tiene una responsabilidad específica.

| Componente    | Tecnología                               | Responsabilidad                                                               |
| ------------- | ---------------------------------------- | ----------------------------------------------------------------------------- |
| Frontend      | React, TypeScript, Vite                  | Interfaz de usuario y comunicación con la API.                                |
| Backend       | Java 21, Spring Boot                     | Autenticación, autorización, gestión de archivos y coordinación del análisis. |
| Data Service  | Python, FastAPI, Pandas, NumPy, OpenPyXL | Lectura, análisis estadístico y comprobaciones de calidad.                    |
| Base de datos | PostgreSQL                               | Persistencia de usuarios, archivos, resultados, columnas e incidencias.       |
| Servidor web  | Nginx                                    | Servir el frontend y redirigir las solicitudes `/api/`.                       |
| Contenedores  | Docker, Docker Compose                   | Construcción y ejecución de los servicios.                                    |
| CI            | GitHub Actions                           | Ejecución de pruebas y validaciones automáticas.                              |

### Flujo de procesamiento

El procesamiento de archivos sigue el siguiente flujo:

1. El usuario inicia sesión desde el frontend.
2. El frontend envía las solicitudes al backend Spring Boot.
3. Spring Boot valida la identidad y los permisos del usuario.
4. El archivo se almacena y su procesamiento se coordina desde el backend.
5. Spring Boot envía el archivo al servicio FastAPI.
6. FastAPI analiza los datos y devuelve un resultado estructurado.
7. Spring Boot persiste las estadísticas y las incidencias en PostgreSQL.
8. El frontend consulta y presenta los resultados.

**Separación de responsabilidades:** el backend es responsable de la autenticación, los permisos y la persistencia. El servicio Python se limita al procesamiento y análisis de datos.

---

## Análisis de datos

DataFlow proporciona estadísticas descriptivas y comprobaciones iniciales de calidad sobre los archivos procesados.

### Estadísticas generales

- Número de filas.
- Número de columnas.
- Cantidad total de valores ausentes.
- Cantidad de filas duplicadas.

### Estadísticas por columna

- Tipo de dato detectado.
- Valores ausentes y porcentaje de ausencia.
- Cantidad y porcentaje de valores únicos.
- Cantidad de valores duplicados.
- Valor mínimo y máximo.
- Media, mediana y desviación estándar para columnas numéricas.
- Cantidad de ceros y valores negativos.

Las estadísticas que no corresponden al tipo de dato de una columna no se calculan.

### Comprobaciones de calidad

El servicio aplica las siguientes reglas básicas:

| Regla                             | Severidad |
| --------------------------------- | --------- |
| Columna completamente vacía       | `HIGH`    |
| Al menos 50 % de valores ausentes | `HIGH`    |
| Presencia de valores ausentes     | `MEDIUM`  |
| Presencia de valores negativos    | `MEDIUM`  |

Estas comprobaciones constituyen una evaluación inicial basada en reglas. Actualmente, el proyecto no implementa un indicador agregado de calidad ni un sistema configurable de reglas.

---

## Estados de los archivos

Los archivos pueden encontrarse en los siguientes estados:

| Estado       | Descripción                                   |
| ------------ | --------------------------------------------- |
| `UPLOADED`   | Archivo cargado y pendiente de procesamiento. |
| `PROCESSING` | Análisis en ejecución.                        |
| `COMPLETED`  | Análisis finalizado correctamente.            |
| `FAILED`     | Procesamiento fallido.                        |

El backend controla las transiciones de estado y evita iniciar simultáneamente varios procesos para el mismo archivo.

También contempla la recuperación de procesamientos interrumpidos y la gestión de eliminaciones pendientes.

---

## Requisitos

Para ejecutar el proyecto mediante Docker Compose se necesita:

- Git.
- Docker Desktop con Docker Compose.
- Una terminal compatible con los comandos de esta guía.

**No es necesario instalar PostgreSQL, Java, Python o Node.js por separado** para ejecutar la aplicación completa mediante contenedores.

---

## Configuración y ejecución

### 1. Clonar el repositorio

```bash
git clone https://github.com/SYOPMRF/dataflow.git

cd dataflow
```

### 2. Crear el archivo de entorno

Crea una copia de la plantilla de configuración:

```bash
cp .env.example .env
```

Edita el archivo `.env` y reemplaza los valores de ejemplo por credenciales propias y seguras.

#### Variables de entorno

| Variable            | Descripción                                                                   |
| ------------------- | ----------------------------------------------------------------------------- |
| `POSTGRES_DB`       | Nombre de la base de datos.                                                   |
| `POSTGRES_USER`     | Usuario administrativo utilizado durante la inicialización y las migraciones. |
| `POSTGRES_PASSWORD` | Contraseña del usuario administrativo.                                        |
| `APP_DB_USER`       | Usuario de ejecución del backend.                                             |
| `APP_DB_PASSWORD`   | Contraseña del usuario de ejecución.                                          |
| `JWT_SECRET`        | Secreto utilizado para firmar los tokens JWT.                                 |
| `JWT_EXPIRATION_MS` | Tiempo de expiración del token en milisegundos.                               |

**Consideraciones de seguridad:**

- Las contraseñas administrativas y de ejecución deben ser diferentes.
- El secreto JWT debe ser aleatorio, suficientemente largo y exclusivo del entorno.
- No utilices los valores de ejemplo de `.env.example` en un entorno real.
- El archivo `.env` está excluido del control de versiones. Nunca publiques credenciales ni secretos.

Para generar un secreto JWT aleatorio en macOS o Linux puedes utilizar:

```bash
openssl rand -base64 48
```

### 3. Construir e iniciar los servicios

Desde la raíz del proyecto, ejecuta:

```bash
docker compose up --build -d
```

Docker Compose se encarga de:

- Construir las imágenes de los servicios.
- Iniciar PostgreSQL y los servicios de aplicación.
- Ejecutar las migraciones.
- Preparar el usuario de base de datos de mínimo privilegio para el backend.

### 4. Comprobar el estado de los servicios

```bash
docker compose ps
```

Para consultar los registros de inicio:

```bash
docker compose logs --tail=100 migrate backend data-service frontend
```

### 5. Acceder a DataFlow

Una vez iniciados los servicios, la aplicación estará disponible en las siguientes direcciones:

| Servicio                            | Dirección                                       |
| ----------------------------------- | ----------------------------------------------- |
| Frontend                            | `http://localhost:3000`                         |
| Backend - salud                     | `http://localhost:8080/actuator/health`         |
| Backend - salud del servicio Python | `http://localhost:8080/api/data-service/health` |
| PostgreSQL desde el host            | `localhost:5433`                                |

**Nota:** FastAPI se ejecuta dentro de la red de Docker y no publica un puerto directamente al host en la configuración principal.

### 6. Detener los servicios

Para detener la aplicación:

```bash
docker compose down
```

Este comando detiene y elimina los contenedores, pero conserva los volúmenes de PostgreSQL y los archivos cargados.

**Importante:** no utilices `docker compose down -v` salvo que quieras eliminar deliberadamente los datos persistidos.

---

## API principal

Las rutas de autenticación son públicas. Las rutas de archivos y análisis requieren autenticación mediante JWT.

| Método   | Endpoint                   | Descripción                                     |
| -------- | -------------------------- | ----------------------------------------------- |
| `POST`   | `/api/auth/register`       | Registrar usuario.                              |
| `POST`   | `/api/auth/login`          | Iniciar sesión.                                 |
| `GET`    | `/api/data-service/health` | Comprobar conexión con el servicio de análisis. |
| `POST`   | `/api/files/upload`        | Cargar un archivo.                              |
| `GET`    | `/api/files`               | Listar archivos del usuario.                    |
| `GET`    | `/api/files/{id}`          | Consultar un archivo.                           |
| `DELETE` | `/api/files/{id}`          | Eliminar un archivo.                            |
| `POST`   | `/api/files/{id}/process`  | Procesar un archivo.                            |
| `GET`    | `/api/files/{id}/analysis` | Consultar el análisis.                          |

### Data Service

El servicio FastAPI dispone de los siguientes endpoints internos:

| Método | Endpoint    | Descripción                       |
| ------ | ----------- | --------------------------------- |
| `GET`  | `/health`   | Comprobar el estado del servicio. |
| `POST` | `/analysis` | Ejecutar el análisis de datos.    |

Estos endpoints se utilizan dentro de la arquitectura de servicios.

---

## Pruebas

El proyecto cuenta con pruebas unitarias, de integración y de extremo a extremo para validar el comportamiento de sus componentes.

### Backend

Desde la raíz del repositorio:

```bash
./backend/mvnw -f backend/pom.xml test
```

Incluye pruebas de:

- Autenticación.
- Permisos y control de acceso.
- Persistencia.
- Procesamiento.
- Recuperación.
- Almacenamiento.
- Aprovisionamiento de usuarios de base de datos.

### Data Service

Desde la raíz del repositorio, crea y activa un entorno virtual de Python:

```bash
cd data-service
python3 -m venv .venv
source .venv/bin/activate
```

Instala las dependencias del servicio:

```bash
python -m pip install -r requirements.txt
```

Ejecuta las pruebas:

```bash
python -m pytest -q
```

Cuando termines, desactiva el entorno virtual y regresa a la raíz del repositorio:

```bash
deactivate
cd ..
```

### Frontend

Desde la raíz del repositorio, accede al directorio del frontend e instala las dependencias:

```bash
cd frontend
npm ci
```

Ejecuta las validaciones del proyecto:

```bash
npm run lint
npm test
npm run build
```

## Integración continua

DataFlow utiliza **GitHub Actions** para automatizar las validaciones del proyecto.

El workflow ejecuta:

- Validaciones del backend.
- Pruebas de extremo a extremo.
- Pruebas del servicio Python.
- Comprobaciones del frontend.

Las ejecuciones se realizan automáticamente en los eventos configurados de `push` y `pull_request` hacia la rama `main`. También se permite la ejecución manual.

**Workflow:** `.github/workflows/ci.yml`.

---

## Estructura del repositorio

```text
dataflow/
│
├── frontend/                 # Aplicación React y TypeScript
│
├── backend/                  # API REST, autenticación y persistencia
│
├── data-service/             # Servicio FastAPI de análisis de datos
│
├── docs/                     # Documentación técnica adicional
│
├── .github/
│   └── workflows/            # Automatización CI
│
├── docker-compose.yml        # Orquestación de servicios
│
├── .env.example              # Plantilla de configuración
│
└── README.md                 # Documentación principal
```

---

## Alcance y evolución

El alcance actual de DataFlow se centra en una aplicación funcional de carga, análisis y gestión de archivos, con persistencia, seguridad básica de aplicación, recuperación ante fallos y automatización de pruebas.

Entre las posibles evoluciones del proyecto se encuentran:

- Despliegue en AWS.
- Infraestructura como código con Terraform.
- Almacenamiento de archivos en servicios cloud.
- Métricas y observabilidad avanzada.
- Mejoras de escalabilidad y rendimiento.
- Reglas de calidad configurables y métricas agregadas.

Estas evoluciones representan posibilidades futuras y **no forman parte de las funcionalidades implementadas actualmente**.

---

## Estado del proyecto

**DataFlow se encuentra en la fase de cierre de su MVP**, con preparación de documentación y demostración técnica.

---
