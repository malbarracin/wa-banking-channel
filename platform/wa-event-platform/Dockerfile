# Imagen de la plataforma de eventos (Java 21 / Spring Boot).
# El contexto de build es la RAÍZ del repositorio: el pom copia los esquemas de
# shared/contracts/events/ al classpath con la ruta relativa ../../shared/contracts/events.
#   docker build -f platform/wa-event-platform/Dockerfile -t wa-event-platform:local .

# ---------- build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build/platform/wa-event-platform

# Dependencias primero: cambian menos que el código y quedan en su propia capa.
COPY platform/wa-event-platform/mvnw ./mvnw
COPY platform/wa-event-platform/.mvn ./.mvn
COPY platform/wa-event-platform/pom.xml ./pom.xml
# El wrapper se ejecuta igual si el repositorio se clonó en Windows (CRLF).
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -ntp -q dependency:go-offline

COPY shared/contracts/events /build/shared/contracts/events
COPY platform/wa-event-platform/src ./src
RUN ./mvnw -B -ntp -q -DskipTests package \
    && JAR="$(ls target/*.jar | head -n 1)" \
    && java -Djarmode=tools -jar "$JAR" extract --layers --launcher --destination /build/extracted

# ---------- runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario sin privilegios: no se ejecuta nada como root.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

WORKDIR /app
COPY --from=build --chown=root:root /build/extracted/dependencies/ ./
COPY --from=build --chown=root:root /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=root:root /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=root:root /build/extracted/application/ ./

ENV SERVER_PORT=8092 \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
EXPOSE 8092

USER 10001:10001

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget -qO- "http://127.0.0.1:${SERVER_PORT}/actuator/health" | grep -q '"status":"UP"'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
