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

El backend implementa una abstracción de almacenamiento mediante la interfaz `FileStorage`, que permite utilizar dos proveedores:

- **LOCAL:** almacena los archivos en el sistema de archivos del backend, utilizando el volumen persistente `uploads_data` en Docker.
- **S3:** almacena los archivos en un servicio compatible con la API de Amazon S3, utilizando AWS SDK for Java 2.x. En el entorno local se utiliza Silo como almacenamiento S3-compatible.

El proveedor predeterminado se configura mediante `FILE_STORAGE_PROVIDER`. La configuración inicial utiliza `LOCAL`; el entorno S3 se activa mediante el overlay `docker-compose.minio.yml`.

Los metadatos de cada archivo se registran en PostgreSQL, incluyendo el proveedor utilizado. Esto permite que cada archivo se lea y elimine mediante el almacenamiento donde fue creado, incluso si posteriormente cambia el proveedor predeterminado.

La incorporación de S3 no migra automáticamente los archivos existentes. Los archivos almacenados localmente conservan su proveedor original.

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

**Tecnologías:** Python, FastAPI, Pandas, NumPy y Pydantic.

**Ubicación:** `data-service/`

El Data Service es un componente independiente encargado de leer, perfilar y analizar los archivos. No gestiona autenticación ni persistencia en PostgreSQL.

Sus responsabilidades son:

- Recibir archivos mediante solicitudes HTTP multipart.
- Leer archivos CSV y XLSX y convertirlos en DataFrames.
- Calcular estadísticas descriptivas y métricas avanzadas por columna.
- Detectar duplicados y posibles claves candidatas.
- Calcular distribuciones y correlaciones entre variables numéricas.
- Ejecutar validaciones de calidad, formato y consistencia semántica.
- Devolver resultados estructurados y validados mediante modelos Pydantic.

El servicio expone los siguientes endpoints:

| Método | Endpoint | Propósito |
| --- | --- | --- |
| `GET` | `/health` | Comprobar disponibilidad. |
| `POST` | `/analysis` | Analizar un archivo CSV o XLSX. |

#### Perfilado y estadísticas

Para cada archivo se calculan:

- Cantidad de filas y columnas.
- Cantidad total de valores ausentes.
- Cantidad de filas duplicadas.

Para cada columna se calculan, cuando corresponda:

- Tipo de dato detectado.
- Cantidad y porcentaje de valores ausentes.
- Cantidad y porcentaje de valores únicos.
- Cantidad de valores duplicados.
- Valor mínimo y máximo.
- Media, mediana y desviación estándar para columnas numéricas.
- Primer y tercer cuartil (Q1 y Q3), rango intercuartílico (IQR) y percentiles 5, 10, 90 y 95.
- Cantidad de ceros y valores negativos.
- Cantidad de valores atípicos detectados mediante IQR y Z-score.

Las métricas que no corresponden al tipo de dato o que no cuentan con observaciones válidas no se calculan.

#### Duplicados y claves candidatas

`DuplicateService` examina combinaciones de columnas para detectar posibles claves candidatas y presenta grupos de valores repetidos con ejemplos de duplicados.

El resultado incluye ejemplos de valores repetidos, frecuencias y metadatos sobre las combinaciones evaluadas. La búsqueda está acotada para controlar su coste.

Las claves candidatas son indicios estadísticos sobre los datos analizados; no representan restricciones de integridad ni garantías sobre datos futuros.

#### Distribuciones

`DistributionService` genera:

- Histogramas para columnas numéricas.
- Distribuciones de frecuencias para columnas categóricas.

Los resultados incluyen los intervalos o categorías y sus frecuencias correspondientes.

#### Correlaciones

`CorrelationService` calcula matrices de correlación:

- **Pearson:** mide la fuerza y dirección de relaciones lineales.
- **Spearman:** mide relaciones monotónicas mediante rangos.

Los resultados incluyen las columnas evaluadas, los coeficientes y el número de observaciones válidas para cada par. Las correlaciones se calculan sobre variables numéricas y pueden incluir coeficientes no disponibles cuando no existen datos suficientes o variación válida.

Una correlación estadística no implica causalidad.

#### Validaciones de calidad

El análisis reúne distintas comprobaciones:

**Reglas generales de calidad**

| Problema | Severidad |
| --- | --- |
| Columna completamente vacía | `HIGH` |
| Al menos 50 % de valores ausentes | `HIGH` |
| Presencia de valores ausentes | `MEDIUM` |
| Presencia de valores negativos | `MEDIUM` |

**Validaciones de formato**

`FormatValidationService` comprueba formatos de correo electrónico, URL y fechas en columnas cuyos nombres coinciden con los identificadores reconocidos por el servicio.

**Validaciones semánticas**

`SemanticValidationService` comprueba determinadas relaciones entre campos de pedidos y entregas. Entre ellas:

- Registros entregados sin fecha de entrega (`HIGH`).
- Descuentos fuera del intervalo permitido de 0 a 100 (`HIGH`).
- Totales inconsistentes con cantidades, precios unitarios y descuentos, aplicando una tolerancia monetaria (`MEDIUM`).

Las validaciones semánticas dependen de la presencia y reconocimiento de los campos necesarios. Los registros incompletos no siempre permiten evaluar una regla.

Las incidencias pueden incluir severidad, columnas relacionadas y cantidad de filas afectadas.

#### Contrato de respuesta

Los resultados se estructuran mediante modelos Pydantic y se devuelven como JSON. El contrato contempla las métricas generales y por columna, incidencias de calidad, análisis de duplicados, distribuciones y correlaciones.

Spring Boot recibe esta respuesta y se encarga de persistirla en PostgreSQL. El Data Service no escribe directamente en la base de datos de la aplicación.

### 2.4. PostgreSQL

**Tecnología:** PostgreSQL 18.

PostgreSQL almacena la información estructurada de la aplicación.

Entre sus responsabilidades se encuentran:

- Persistir usuarios.
- Registrar metadatos de archivos y el proveedor de almacenamiento asociado.
- Mantener los estados de procesamiento.
- Guardar los resultados generales de análisis.
- Guardar estadísticas descriptivas y avanzadas por columna.
- Registrar incidencias de calidad y sus metadatos.
- Mantener información de eliminaciones pendientes.

#### Persistencia de resultados de análisis

Los resultados se distribuyen en las siguientes entidades principales:

- `AnalysisResultEntity`: conserva las métricas generales del archivo y los bloques de análisis avanzado.
- `FileColumn`: conserva las estadísticas y métricas calculadas para cada columna.
- `DataQualityIssueEntity`: conserva las incidencias detectadas, su severidad, las columnas relacionadas y la cantidad de filas afectadas.

Los análisis de duplicados, distribuciones y correlaciones se almacenan como documentos JSONB asociados al resultado general. Esto permite conservar sus estructuras sin convertir cada elemento de esos análisis en una tabla independiente.

La persistencia se realiza desde Spring Boot mediante `AnalysisPersistenceService` dentro de una transacción. La consulta posterior reconstruye el resultado utilizando los datos relacionales y los bloques JSONB almacenados.

#### Migraciones y permisos

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
7. **Análisis:** el Data Service calcula las estadísticas, detecta duplicados y claves candidatas, genera distribuciones y correlaciones, y ejecuta las validaciones de calidad, formato y consistencia semántica.
8. **Respuesta:** FastAPI devuelve los resultados en JSON.
9. **Persistencia:** Spring Boot guarda las métricas generales y por columna, las incidencias de calidad y los bloques de análisis avanzado dentro de una transacción.
10. **Finalización:** el archivo pasa a `COMPLETED`.
11. **Consulta:** el frontend solicita los resultados al backend.

Si ocurre un error durante el procesamiento o la persistencia, el backend intenta marcar el archivo como `FAILED`, permitiendo un nuevo intento posteriormente.

---

## 5. Eliminación y recuperación

DataFlow incorpora mecanismos para manejar situaciones en las que una operación no termina como se esperaba.

### 5.1. Eliminación de archivos

La eliminación de un archivo contempla tanto sus registros asociados como su contenido físico.

El backend registra una tarea de eliminación pendiente dentro de la misma transacción que elimina los metadatos del archivo y sus resultados asociados.

La limpieza física se intenta después de confirmar la transacción mediante un mecanismo de sincronización de Spring. Si la eliminación física falla, la tarea permanece registrada en PostgreSQL para permitir nuevos intentos.

Cada tarea conserva el nombre almacenado y el proveedor de almacenamiento original (`LOCAL` o `S3`), de modo que la eliminación se ejecuta en el almacenamiento correcto, independientemente de la configuración predeterminada actual.

Un servicio programado procesa periódicamente las tareas pendientes en lotes de hasta 50 elementos. El mecanismo utiliza:

- Bloqueos de filas con `FOR UPDATE SKIP LOCKED` para coordinar la reclamación de tareas entre trabajadores.
- Tokens de reclamación y períodos de vigencia para controlar la propiedad temporal de cada tarea.
- Reintentos con espera progresiva, limitada a un máximo de 60 minutos entre intentos.
- Registro del número de intentos y del último error.

Este mecanismo permite recuperar eliminaciones que no pudieron completarse por interrupciones o fallos temporales del almacenamiento.

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

| Volumen         | Descripción                                    |
| --------------- | ---------------------------------------------- |
| `postgres_data` | Almacena los datos persistentes de PostgreSQL. |
| `uploads_data`  | Almacena los archivos del proveedor `LOCAL`.   |

### Almacenamiento S3-compatible local

De forma opcional, se puede incorporar PGSTY Silo mediante el overlay `docker-compose.minio.yml`.

Este servicio implementa una API compatible con Amazon S3 y permite probar el almacenamiento de objetos localmente sin depender de AWS.

El overlay añade:

- **silo:** servicio de almacenamiento de objetos.
- `minio_data`: volumen persistente para los objetos almacenados.

Los puertos se publican únicamente en la interfaz local del host:

- `9000`: API S3.
- `9001`: consola web de administración.

El backend se conecta a Silo mediante la red interna de Docker y utiliza credenciales específicas de aplicación con permisos limitados sobre el bucket configurado.

Sin el overlay, DataFlow utiliza el proveedor `LOCAL`.

### Infraestructura como código con Terraform

El directorio terraform/ organiza las configuraciones de infraestructura como código por entorno:

`terraform/local-silo/:` contiene la configuración Terraform que gestiona el bucket dataflow-files mediante el proveedor AWS con un endpoint personalizado hacia PGSTY Silo (http://127.0.0.1:9000). Permite practicar la gestión declarativa de recursos compatibles con S3 sin crear infraestructura en AWS ni generar costos asociados.

`terraform/aws/:` está reservado para la futura configuración de infraestructura real en AWS. Actualmente no contiene recursos ni configuraciones desplegables.

La configuración contempla:

- Declaración del bucket `dataflow-files`.

- Importación del bucket existente al estado de Terraform.

- Definición de salidas para consultar el nombre y ARN del recurso.

- Fijación de la versión del proveedor mediante `.terraform.lock.hcl`.

La configuración local fue inicializada y validada. También se comprobó que el estado de Terraform conserva el recurso importado y que un plan con actualización del estado (refresh) no detecta diferencias con el bucket existente.

Los archivos de estado, planes generados y directorios de trabajo de Terraform se excluyen del control de versiones. El archivo .terraform.lock.hcl de cada entorno sí se conserva en el repositorio.

**Alcance actual:** únicamente está implementada y validada la gestión del bucket S3-compatible local. No representa un despliegue en AWS ni configura otros servicios de infraestructura cloud. La configuración de AWS se desarrollará posteriormente, de forma independiente.

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

DataFlow ya incorpora almacenamiento mediante la API compatible con Amazon S3, probado localmente con PGSTY Silo. Por tanto, esta capacidad forma parte de la implementación actual y no se considera una funcionalidad pendiente.

Las siguientes tecnologías y capacidades se consideran posibles evoluciones:

- **Despliegue en AWS:** trasladar la infraestructura local a servicios administrados en la nube.
- **Amazon S3:** utilizar el servicio de almacenamiento de objetos de AWS con la integración existente, adaptando las credenciales, permisos y configuración del entorno.
- **Infraestructura como código mediante Terraform:** automatizar el aprovisionamiento de infraestructura.
- **Monitoreo avanzado con Prometheus y Grafana:** incorporar métricas, paneles y alertas.
- **Orquestación con Kubernetes:** evaluar su incorporación si las necesidades del proyecto lo justifican.

La migración de archivos existentes entre proveedores de almacenamiento requerirá un procedimiento específico, ya que DataFlow no la realiza automáticamente.
