# syntax=docker/dockerfile:1
# Build: compila con el Maven Wrapper del componente (la imagen JDK no trae Maven)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
# El wrapper descarga Maven con curl y unzip (no vienen en la imagen alpine)
RUN apk add --no-cache curl unzip

COPY mvnw pom.xml ./
COPY .mvn .mvn
# En Windows el wrapper puede llegar con CRLF: se normaliza antes de usarlo
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q package -DskipTests && \
    java -Djarmode=tools -jar target/wa-users-service-*.jar extract --layers --launcher --destination extracted

# Runtime: solo la JRE, sin privilegios
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

# Capas en orden de cambio (las dependencias casi no cambian: quedan en caché)
COPY --from=builder --chown=app:app /app/extracted/dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /app/extracted/application/ ./

ENV SERVER_PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
