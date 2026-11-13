# syntax=docker/dockerfile:1

# ---- Etapa 1: compilación con Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Primero solo el pom: mientras no cambien las dependencias, esta capa y la caché de Maven se reutilizan.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q dependency:go-offline

COPY src ./src
# Las pruebas (que usan Testcontainers) se ejecutan fuera de la imagen: aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -q -DskipTests package \
    && cp target/messaging-service-*.jar /build/app.jar

# ---- Etapa 2: ejecución con JRE 17 ----
# Misma base que radar-service (Debian/Ubuntu con glibc); este servicio no usa librerías nativas.
FROM eclipse-temurin:17-jre
# El HEALTHCHECK necesita curl: si la imagen base deja de traerlo, el build falla aquí y no en producción.
RUN command -v curl >/dev/null

# Usuario sin privilegios
RUN groupadd --system --gid 10001 messaging \
    && useradd --system --uid 10001 --gid messaging --no-create-home --shell /usr/sbin/nologin messaging

WORKDIR /app
COPY --from=build --chown=messaging:messaging /build/app.jar /app/app.jar
USER messaging:messaging

EXPOSE 8080

# El 503 de /actuator/health (por ejemplo, MySQL caído) marca el contenedor como unhealthy.
# El periodo de gracia cubre el arranque: migraciones de Flyway y conexión a MySQL.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

# La memoria se ajusta al límite del contenedor.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
