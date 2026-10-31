# Build desde la raíz del repositorio (incluye shared/contracts/events):
#   docker build -f platform/wa-channel-orchestrator/Dockerfile -t wa-channel-orchestrator:local .
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY platform/wa-channel-orchestrator/mvnw platform/wa-channel-orchestrator/pom.xml ./platform/wa-channel-orchestrator/
COPY platform/wa-channel-orchestrator/.mvn ./platform/wa-channel-orchestrator/.mvn
COPY platform/wa-channel-orchestrator/src ./platform/wa-channel-orchestrator/src
COPY shared/contracts/events ./shared/contracts/events

WORKDIR /workspace/platform/wa-channel-orchestrator
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

RUN ./mvnw -B -q package -DskipTests && \
    java -Djarmode=layertools -jar target/*.jar extract

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app

COPY --from=builder /workspace/platform/wa-channel-orchestrator/dependencies/ ./
COPY --from=builder /workspace/platform/wa-channel-orchestrator/spring-boot-loader/ ./
COPY --from=builder /workspace/platform/wa-channel-orchestrator/snapshot-dependencies/ ./
COPY --from=builder /workspace/platform/wa-channel-orchestrator/application/ ./

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"
ENV SERVER_PORT=8091

EXPOSE 8091
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://localhost:8091/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
