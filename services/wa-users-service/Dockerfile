# wa-users-service — JVM multi-stage (Spring Boot fat JAR)
# GraalVM native-image tag ghcr.io/graalvm/native-image:java21 was removed;
# use Dockerfile.native (community image) if native build is required.
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget
COPY --from=builder /build/target/wa-users-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
