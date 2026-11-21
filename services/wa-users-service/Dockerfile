# syntax=docker/dockerfile:1

# ---- Build: JDK + Maven Wrapper ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Primero el wrapper y el POM: las dependencias quedan en una capa que solo se invalida si cambia el pom.xml.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Un checkout en Windows puede dejar el wrapper con CRLF; en Linux tiene que ser LF.
RUN sed -i 's/\r$//' mvnw .mvn/wrapper/maven-wrapper.properties && chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src src
# Los tests y la cobertura corren en CI (./mvnw verify); acá solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests -Djacoco.skip=true \
    && cp target/wa-users-service-*.jar /workspace/app.jar

# ---- Runtime: JRE, usuario sin privilegios ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
