---
name: java-spring-development
description: Java Spring Boot development guidelines with best practices for building robust, secure, and maintainable enterprise applications
---

# Java Spring Development Best Practices

## Core Principles

- Write clean, efficient, and well-documented Java code with accurate Spring Boot examples
- Use Spring Boot 3.x with Java 21 (records, sealed classes, pattern matching for switch). New projects: `<java.version>21</java.version>`
- Prefer constructor injection over field injection for better testability
- Follow SOLID principles and RESTful API design patterns
- Design for microservices architecture suitability

## Project Structure

Use the package layout of `architecture.md` (by domain module, with `controller/service/repository/mapper/entity/dto`
sub-packages). Do not use a global `controllers/`, `services/`, `models/` layout.

## Dependency Injection

- Use constructor injection for required dependencies
- Leverage `@RequiredArgsConstructor` with Lombok for cleaner code
- Keep constructors simple and avoid logic in them
- Use `@Qualifier` when multiple implementations exist

## REST API Design

- Use appropriate HTTP methods (GET, POST, PUT, DELETE, PATCH)
- Return proper HTTP status codes
- Errors: the single error contract (skill `error-contract`)
- Use DTOs to control API contract
- Versioning: skill `api-versioning`

## Data Access

### Spring Data JPA
- Define proper entity relationships (@OneToMany, @ManyToOne, etc.)
- Use lazy loading appropriately to avoid N+1 queries
- Implement pagination for large result sets
- Use query methods and @Query for custom queries

### Database Migrations
- Use Flyway or Liquibase for schema migrations
- Version migration scripts properly
- Never modify existing migrations
- Test migrations in development before production

## Security

### Spring Security
- Implement authentication and authorization properly
- Use BCrypt for password encoding
- Configure CORS appropriately
- Protect endpoints based on roles/permissions
- Use HTTPS in production

### Secure Coding
- Validate all user inputs
- Sanitize data to prevent injection attacks
- Avoid exposing sensitive information in responses
- Use parameterized queries

## Testing

See `backend-java-test` and `java-testing` (JUnit 5, Mockito, MockMvc, Testcontainers; JaCoCo ≥ 80%).

## Performance

### Caching
- Use Spring Cache abstraction
- Configure appropriate cache providers (Redis, Caffeine)
- Set proper TTL for cached data
- Implement cache eviction strategies

### Async Processing
- Use @Async for non-blocking operations
- Configure thread pools appropriately
- Handle exceptions in async methods
- Consider using reactive patterns for high concurrency

## Logging and Monitoring

### Logging
- Use SLF4J with Logback
- Log at appropriate levels
- Include correlation IDs for tracing
- Avoid logging sensitive data

### Monitoring
- Use Spring Boot Actuator for health and metrics
- Export metrics to monitoring systems
- Set up proper health checks
- Monitor application performance

## API Documentation

Skill `api-openapi-docs` (springdoc, examples on every endpoint, exported `delivery/openapi/`).
