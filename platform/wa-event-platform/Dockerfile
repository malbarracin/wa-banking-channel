# Build desde la raíz del repositorio (incluye shared/contracts/events):
#   docker build -f platform/wa-event-platform/Dockerfile -t wa-event-platform:local .
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY platform/wa-event-platform/mvnw platform/wa-event-platform/pom.xml ./platform/wa-event-platform/
COPY platform/wa-event-platform/.mvn ./platform/wa-event-platform/.mvn
COPY platform/wa-event-platform/src ./platform/wa-event-platform/src
COPY shared/contracts/events ./shared/contracts/events

WORKDIR /workspace/platform/wa-event-platform
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

RUN ./mvnw -B -q package -DskipTests && \
    java -Djarmode=layertools -jar target/*.jar extract

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY --from=builder /workspace/platform/wa-event-platform/dependencies/ ./
COPY --from=builder /workspace/platform/wa-event-platform/spring-boot-loader/ ./
COPY --from=builder /workspace/platform/wa-event-platform/snapshot-dependencies/ ./
COPY --from=builder /workspace/platform/wa-event-platform/application/ ./

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"
ENV SERVER_PORT=8092

EXPOSE 8092
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8092/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
