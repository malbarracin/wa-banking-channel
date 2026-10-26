---
title: Usuarios — arquitectura
status: borrador
source: null
updated: 2026-10-26T12:39:37.210Z
---

# Arquitectura — Servicio de Usuarios

## Base
- Base package: com.bcra.users.ws (from project.yaml java.base_package)
- Maven groupId: com.bcra (from project.yaml java.group_id)
- Módulo de dominio: user

## Árbol de paquetes
com.bcra.users.ws/
├── UserApplication.java
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── mapper/
│   ├── entity/
│   └── dto/
├── config/
└── exception/

## Entidades
- user.entity.User (id UUID, documento tipo+numero unique, nombre, apellido, fechaNacimiento, email, telefono, nombreTratamiento, preferencias, estado enum ACTIVE/SUSPENDED/LOGICAL_DELETE, auditoria)
- user.entity.Documento (embeddable tipo + numero)
- user.entity.UserStatus enum

## DTOs
- user.dto.CreateUserRequest (documento, nombre, apellido, fechaNacimiento, email?, telefono?)
- user.dto.UpdateUserRequest (email?, telefono?, nombreTratamiento?, preferencias?)
- user.dto.UserResponse (id, documento, nombre, apellido, fechaNacimiento, email, telefono, nombreTratamiento, preferencias, estado)
- user.dto.UserStatusResponse

## Endpoints
- POST /api/v1/users → 201 Created, 400 VALIDATION_ERROR, 409 CONFLICT (duplicate document)
- GET /api/v1/users/{tipo}/{numero} → 200, 404 NOT_FOUND
- PATCH /api/v1/users/{tipo}/{numero} → 200, 400, 404
- PATCH /api/v1/users/{tipo}/{numero}/status → 200, 400, 404
- GET /api/v1/users/{tipo}/{numero}/status → 200, 404 (for channel link eligibility)

## Mappers
- user.mapper.UserMapper (MapStruct)

## Repositories
- user.repository.UserRepository (JpaRepository, findByDocumentoTipoAndDocumentoNumero)

## Services
- user.service.UserService (alta, consulta, update, cambio estado, auditoria)
- user.service.AuditService (registro fecha, accion, resultado)

## Error contract
- exception/ErrorResponse, FieldError, ErrorCode, GlobalExceptionHandler

## Tests
- Unit + integration, JaCoCo >= 80%

## Riesgos
- Campos de consulta parametrizables por perfil (Compliance pendiente)
- Reglas de transición de estados parametrizables
- Validaciones de formato (tipos de documento, email, telefono) configurables
