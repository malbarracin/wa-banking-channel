# wa-users-service — GraalVM native
FROM ghcr.io/graalvm/native-image:java21 AS builder
WORKDIR /build
COPY . .
RUN ./mvnw -q -Pnative native:compile -DskipTests

FROM gcr.io/distroless/base-debian12
COPY --from=builder /build/target/wa-users-service /app
EXPOSE 8080
ENTRYPOINT ["/app"]
