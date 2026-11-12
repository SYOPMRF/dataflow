# DataFlow

**DataFlow** es una aplicación web para cargar, procesar y analizar archivos CSV y XLSX. Proporciona perfilado estadístico, detección de duplicados y claves candidatas, análisis de distribuciones y correlaciones, y validaciones de calidad de datos, con gestión de archivos y resultados persistidos por usuario.

El proyecto está construido como una arquitectura de servicios separados, con autenticación, persistencia, procesamiento de datos, contenedores y pruebas automatizadas.

---

## Funcionalidades

- Registro e inicio de sesión mediante autenticación JWT.
- Hash de contraseñas mediante BCrypt.
- Carga de archivos CSV y XLSX de hasta 10 MB.
- Consulta y eliminación de archivos propios.
- Almacenamiento de archivos mediante proveedores `LOCAL` y `S3`.
- Integración S3-compatible local mediante PGSTY Silo y AWS SDK for Java 2.x.
- Persistencia del proveedor de almacenamiento asociado a cada archivo.
- Procesamiento de archivos mediante un servicio independiente en Python.
- Estadísticas descriptivas y perfilado avanzado por columna.
- Detección de duplicados y posibles claves candidatas.
- Análisis de distribuciones numéricas y categóricas.
- Correlaciones Pearson y Spearman.
- Validaciones generales, de formato y consistencia semántica.
- Persistencia de resultados de análisis en PostgreSQL.
- Control de acceso por usuario.
- Recuperación de archivos cuyo procesamiento quedó interrumpido.
- Eliminación diferida con persistencia de tareas y reintentos automáticos.
- Ejecución de los servicios mediante Docker Compose.
- Integración continua con GitHub Actions.

## Arquitectura

DataFlow utiliza una arquitectura de servicios separados, donde cada componente tiene una responsabilidad específica.

| Componente                         | Tecnología                                       | Responsabilidad                                                                           |
| ---------------------------------- | ------------------------------------------------ | ----------------------------------------------------------------------------------------- |
| Frontend                           | React, TypeScript, Vite                          | Interfaz de usuario y comunicación con la API.                                            |
| Backend                            | Java 21, Spring Boot                             | Autenticación, autorización, gestión de archivos y coordinación del análisis.             |
| Data Service                       | Python, FastAPI, Pandas, NumPy, OpenPyXL         | Lectura, análisis estadístico y comprobaciones de calidad.                                |
| Base de datos                      | PostgreSQL                                       | Persistencia de usuarios, archivos, resultados, columnas e incidencias.                   |
| Almacenamiento                     | FileStorage, sistema local, AWS SDK for Java 2.x | Abstracción para almacenar, leer y eliminar archivos mediante proveedores `LOCAL` o `S3`. |
| Almacenamiento local S3-compatible | PGSTY Silo                                       | Servicio local de almacenamiento de objetos compatible con Amazon S3.                     |
| Servidor web                       | Nginx                                            | Servir el frontend y redirigir las solicitudes `/api/`.                                   |
| Contenedores                       | Docker, Docker Compose                           | Construcción y ejecución de los servicios.                                                |
| CI                                 | GitHub Actions                                   | Ejecución de pruebas y validaciones automáticas.                                          |

### Flujo de procesamiento

El procesamiento de archivos sigue el siguiente flujo:

1. El usuario inicia sesión desde el frontend.
2. El frontend envía las solicitudes al backend Spring Boot.
3. Spring Boot valida la identidad y los permisos del usuario.
4. El archivo se almacena y su procesamiento se coordina desde el backend.
5. Spring Boot envía el archivo al servicio FastAPI.
6. FastAPI analiza los datos y devuelve un resultado estructurado.
7. Spring Boot persiste las estadísticas, los análisis avanzados y las incidencias en PostgreSQL.
8. El frontend consulta y presenta los resultados.

**Separación de responsabilidades:** el backend es responsable de la autenticación, los permisos y la persistencia. El servicio Python se limita al procesamiento y análisis de datos.

### Almacenamiento de archivos

El backend utiliza la interfaz `FileStorage` para desacoplar la gestión de archivos del proveedor de almacenamiento.

Actualmente se admiten dos proveedores:

| Proveedor | Descripción                                                                                                              |
| --------- | ------------------------------------------------------------------------------------------------------------------------ |
| `LOCAL`   | Almacena los archivos en el sistema de archivos del backend, utilizando el volumen persistente `uploads_data` en Docker. |
| `S3`      | Almacena los archivos mediante la API compatible con Amazon S3, utilizando AWS SDK for Java 2.x.                         |

Para el entorno local, la integración S3 se prueba mediante **PGSTY Silo**, un servicio compatible con la API de Amazon S3.

El proveedor predeterminado se configura mediante `FILE_STORAGE_PROVIDER`. La configuración base utiliza `LOCAL`, mientras que el overlay `docker-compose.minio.yml` activa el proveedor `S3`.

PostgreSQL conserva el proveedor asociado a cada archivo. Por eso, los archivos existentes pueden seguir leyéndose y eliminándose desde su almacenamiento original aunque cambie el proveedor predeterminado.

**Importante:** cambiar de proveedor no migra automáticamente los archivos existentes.

---

## Análisis de datos

DataFlow ofrece un análisis de perfilado, estadística descriptiva y calidad de datos para archivos CSV y XLSX.

### Estadísticas generales

- Número de filas y columnas.
- Cantidad total de valores ausentes.
- Cantidad de filas duplicadas.

### Estadísticas por columna

- Tipo de dato detectado.
- Valores ausentes y porcentaje de ausencia.
- Cantidad y porcentaje de valores únicos.
- Cantidad de valores duplicados.
- Valor mínimo y máximo.
- Media, mediana y desviación estándar para columnas numéricas.
- Cuartiles (Q1 y Q3), rango intercuartílico (IQR) y percentiles 5, 10, 90 y 95.
- Cantidad de ceros y valores negativos.
- Cantidad de valores atípicos detectados mediante IQR y Z-score.

Las métricas numéricas se calculan cuando corresponden al tipo de dato y existen observaciones válidas.

### Duplicados y claves candidatas

El análisis identifica filas duplicadas y examina combinaciones de columnas para detectar posibles claves candidatas. También presenta grupos de valores repetidos y ejemplos de duplicados.

Una clave candidata es una combinación que parece identificar registros de forma única en los datos evaluados; no constituye una restricción ni una garantía de integridad en la base de datos original.

### Distribuciones

- Histogramas para variables numéricas.
- Distribuciones de frecuencias para variables categóricas.

### Correlaciones

Se calculan matrices de correlación Pearson y Spearman para variables numéricas, junto con el número de observaciones válidas utilizado en cada par.

Pearson mide relaciones lineales y Spearman relaciones monotónicas. Una correlación no demuestra causalidad.

### Comprobaciones de calidad

El análisis incluye reglas sobre:

| Comprobación | Severidad |
| --- | --- |
| Columna completamente vacía | `HIGH` |
| Al menos 50 % de valores ausentes | `HIGH` |
| Presencia de valores ausentes | `MEDIUM` |
| Presencia de valores negativos | `MEDIUM` |

También incorpora validaciones de formato para columnas reconocidas como correo electrónico, URL y fechas, y validaciones semánticas para determinados datos de pedidos, descuentos y entregas.

Entre las incidencias semánticas detectadas se encuentran entregas sin fecha, descuentos fuera del intervalo permitido y totales de pedidos inconsistentes con sus cantidades, precios y descuentos.

Las incidencias pueden incluir columnas relacionadas y cantidad de filas afectadas cuando esa información está disponible.

### Alcance y limitaciones del análisis

Los resultados se persisten en PostgreSQL y pueden consultarse posteriormente sin repetir el procesamiento.

El análisis tiene límites deliberados: algunas evaluaciones están acotadas para controlar su coste, las correlaciones requieren observaciones numéricas válidas y las validaciones semánticas dependen de nombres de columnas reconocidos y de los campos disponibles.

DataFlow no implementa actualmente un indicador agregado de calidad ni un sistema de reglas configurable. Las correlaciones tampoco permiten establecer relaciones causales.

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

| Variable                     | Descripción                                                                       |
| ---------------------------- | --------------------------------------------------------------------------------- |
| `POSTGRES_DB`                | Nombre de la base de datos.                                                       |
| `POSTGRES_USER`              | Usuario administrativo utilizado durante la inicialización y las migraciones.     |
| `POSTGRES_PASSWORD`          | Contraseña del usuario administrativo.                                            |
| `APP_DB_USER`                | Usuario de ejecución del backend.                                                 |
| `APP_DB_PASSWORD`            | Contraseña del usuario de ejecución.                                              |
| `JWT_SECRET`                 | Secreto utilizado para firmar los tokens JWT.                                     |
| `JWT_EXPIRATION_MS`          | Tiempo de expiración del token en milisegundos.                                   |
| `MINIO_ROOT_USER`            | Usuario administrativo de PGSTY Silo. Necesario para la modalidad S3.             |
| `MINIO_ROOT_PASSWORD`        | Contraseña administrativa de PGSTY Silo. Necesaria para la modalidad S3.          |
| `FILE_STORAGE_S3_ACCESS_KEY` | Clave de acceso del usuario de aplicación con permisos limitados sobre el bucket. |
| `FILE_STORAGE_S3_SECRET_KEY` | Clave secreta del usuario de aplicación.                                          |

**Almacenamiento S3-compatible:** las cuatro variables adicionales solo son necesarias cuando se utiliza el overlay `docker-compose.minio.yml`. Las credenciales administrativas de Silo deben ser diferentes de las credenciales específicas del backend. No se deben compartir ni publicar estos valores.

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

### 6. Ejecutar con almacenamiento S3-compatible

La configuración S3 es opcional y utiliza PGSTY Silo como almacenamiento de objetos local.

Antes de iniciar esta modalidad:

1. Configurar en `.env` las cuatro variables de Silo y del usuario de aplicación documentadas anteriormente.
2. Iniciar los servicios utilizando el archivo Compose principal y el overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml up --build -d
```

3. Acceder a la consola de Silo:

   `http://localhost:9001`

4. Crear el bucket privado `dataflow-files`.
5. Crear un usuario de aplicación y asignarle una política con permisos limitados sobre ese bucket. La política debe permitir `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` y `s3:ListBucket`.
6. Comprobar que los servicios estén iniciados:

```bash
docker compose -f docker-compose.yml -f docker-compose.minio.yml ps
```

El backend utiliza las credenciales del usuario de aplicación, no las credenciales administrativas de Silo.

En esta modalidad, la API S3 está disponible dentro de Docker en `http://silo:9000`. La consola web se publica localmente en el puerto `9001`.

### 7. Detener los servicios

Para detener la aplicación con almacenamiento `LOCAL`:

```bash id="m3g5fa"
docker compose down
```

Para detener la aplicación con almacenamiento `S3`:

```bash id="q8r2ve"
docker compose -f docker-compose.yml -f docker-compose.minio.yml down
```

Ambos comandos detienen y eliminan los contenedores y las redes del entorno, pero conservan los volúmenes persistentes.

**Importante:** no utilices `docker compose down -v` salvo que quieras eliminar deliberadamente los datos persistidos. Esto incluye los archivos locales y los objetos almacenados en Silo.

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
├── docker-compose.yml        # Orquestación principal de servicios
│
├── docker-compose.minio.yml  # Overlay para almacenamiento S3 con Silo
│
├── .env.example              # Plantilla de configuración
│
└── README.md                 # Documentación principal
```

---

## Alcance y evolución

El alcance actual de DataFlow comprende una aplicación funcional de carga, análisis y gestión de archivos, con persistencia, autenticación, control de acceso, recuperación ante fallos y automatización de pruebas.

El almacenamiento admite los proveedores `LOCAL` y `S3`. La integración S3 se encuentra implementada y probada localmente mediante PGSTY Silo, utilizando AWS SDK for Java 2.x.

La infraestructura como código también cuenta con una configuración inicial de Terraform, validada contra el bucket S3-compatible local. Esta configuración permite gestionar declarativamente el almacenamiento de objetos sin depender de una cuenta de AWS.

Entre las posibles evoluciones del proyecto se encuentran:

- Despliegue de la aplicación en AWS.
- Uso de Amazon S3 como servicio de almacenamiento en la nube.
- Ampliación de Terraform para gestionar infraestructura real en AWS, incluyendo los servicios necesarios para el despliegue.
- Métricas y observabilidad avanzada con Prometheus y Grafana.
- Mejoras de escalabilidad y rendimiento.
- Reglas de calidad configurables y métricas agregadas.
- Orquestación con Kubernetes, si las necesidades del proyecto lo justifican.

Estas evoluciones representan posibilidades futuras y no deben confundirse con las funcionalidades ya implementadas.

---

## Estado del proyecto

**DataFlow cuenta con las funcionalidades principales de su MVP implementadas.**

La Fase 1 de análisis avanzado está desarrollada y validada localmente mediante pruebas automatizadas y una demostración funcional de extremo a extremo.

El cierre formal queda sujeto a la revisión final de los cambios, la validación de integración continua sobre la versión actual y la preparación del commit correspondiente.

---
