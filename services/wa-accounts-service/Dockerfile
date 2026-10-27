# Build: compila con el Maven Wrapper del proyecto (la imagen JDK no trae Maven)
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q package -DskipTests && \
    java -Djarmode=layertools -jar target/*.jar extract

# Runtime: solo la JRE, sin privilegios
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

# Capas en orden de cambio (las dependencias casi no cambian: quedan en caché)
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
