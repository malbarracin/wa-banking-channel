# Pull Request — H4 Servicio de Cuentas (Piloto F1)

## Título sugerido

`feat(h4): servicio de cuentas F1 — listado y saldo con auth H2`

## Tipo de cambio

- [x] Nueva feature (greenfield)
- [ ] Bug fix
- [ ] Breaking change
- [ ] Documentación

**Breaking changes:** ninguno (servicio nuevo).

---

## Summary

Implementa `wa-accounts-service` (H4) piloto F1 para el canal WhatsApp bancario:

- **`GET /api/v1/accounts`** — listado de cuentas del titular autenticado.
- **`GET /api/v1/accounts/{accountId}/balance`** — saldo disponible y contable.

Autenticación obligatoria vía validación de credencial H2 (`Authorization: Bearer` + `X-Credential-Id`). Scope por `bankUserId` retornado por validate. Error contract estándar con mensaje 401 orientado al cliente H3.

Stack: Java 21, Spring Boot 3.3.5, MongoDB 7, MapStruct, springdoc OpenAPI, JaCoCo.

---

## Cambios principales

| Área | Detalle |
|------|---------|
| API REST | 2 endpoints F1 bajo `/api/v1/accounts` |
| Seguridad | `SessionAuthenticationFilter` + `SessionCredentialValidator` → H2 validate |
| Persistencia | `AccountEntity` en MongoDB, seed demo perfil `local` |
| Integración | `integration.session.base-url` (H2 `:8082`), stub configurable |
| Docs | README, Postman, OpenAPI con ejemplos |
| Tests | 26 tests (unit + IT Testcontainers), coverage LINE ~86% |
| Release bundle | `backlog/exports/h4-servicio-cuentas/` |

---

## Test plan

- [ ] `./mvnw clean verify` — build verde, JaCoCo ≥ 70%
- [ ] `docker compose up -d mongo` + Plan A JAR en `:8083`
- [ ] `GET /actuator/health` → UP
- [ ] `GET /api/v1/accounts` con headers demo → 200 + listado
- [ ] `GET /api/v1/accounts` sin headers → 401 + mensaje spec
- [ ] `GET /api/v1/accounts/{id}/balance` id válido → 200
- [ ] `GET /api/v1/accounts/missing-id/balance` → 404
- [ ] `/v3/api-docs` y `/swagger-ui/index.html` responden
- [ ] Postman collection F1 ejecutable
- [ ] (Opcional E2E) H2 `:8082` stub off + credencial real emitida por H3

---

## Checklist PR

- [x] Compila y tests pasan
- [x] Error contract respetado
- [x] Sin exposición de Entity en API
- [x] Auth H2 en todos los endpoints F1
- [x] README y bundle release actualizados
- [x] Review APPROVED (TASK-39)
- [x] Sin dependencias extra no justificadas
- [x] JaCoCo aislado de runtime (`jacocoArgLine`)

---

## Referencias

- Epic: TASK-33 — H4 Servicio de Cuentas
- Spec Notion: H4 — Servicio de Cuentas (piloto F1)
- Dependencia H2: `backlog/exports/h2-servicio-acceso-sesion/`
- Consumidor H3: `backlog/exports/h3-servicio-canal-whatsapp/`
