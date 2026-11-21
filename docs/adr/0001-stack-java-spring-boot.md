# 1. Stack Java / Spring Boot para el Servicio de Usuarios

Fecha: 2026-11-21
Estado: Aceptado

## Contexto

`wa-users-service` administra clientes del banco: alta, consulta, actualización y estados. Es un dominio
transaccional (CRUD con reglas de unicidad, transiciones de estado y auditoría), no un componente de IA. El programa
usa un stack Java para los servicios de dominio y un stack Python/IA para los componentes conversacionales.

## Decisión

Implementar el servicio en **Java 21 + Spring Boot 3.x**, con **Spring Web (MVC)**, **Spring Data JPA** sobre
**PostgreSQL**, **Maven + `./mvnw`**, **Lombok**, **MapStruct** y **JaCoCo (umbral 80%)**. Es el stack del pack Java
del programa y el que usan los demás servicios de dominio.

## Consecuencias

- Consistencia con el resto de los servicios de dominio y reuso de skills/patrones (error-contract, versionado).
- Persistencia relacional (JPA + Flyway); **no** se usan pgvector/Redis (son para componentes de IA).
- Build con wrapper `./mvnw`; `./mvnw clean verify` es la verificación de cierre.
- Java 21 aprovecha `record` para DTOs inmutables y `switch` con pattern matching en las transiciones de estado.