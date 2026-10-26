FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

RUN chmod +x mvnw

COPY src/ src/

RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd --system --user-group appuser \
    && mkdir -p /app/uploads \
    && chown appuser:appuser /app/uploads

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

RUN chown appuser:appuser app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
