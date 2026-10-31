---
title: "Revisión de PRs #2, #3 y #4 — Plan de arquitectura"
status: borrador
source: null
updated: 2026-10-31T12:21:52.817Z
---

# Revisión de PRs #2, #3 y #4 — Plan de arquitectura

## Alcance

Resolver los comentarios de revisión de los PR #2 (H1 Usuarios), #3 (H0 canal) y #4 (H2 credenciales). 11 puntos: 5 HIGH, 6 MEDIUM. Entrega: spec para que tech-lead parta en tareas.

## Puntos y enfoque

### PR #2 H1 Usuarios

1. **Audit de altas rechazadas (HIGH)** — `UserController.alta`: registrar `AuditResult.RECHAZADO` cuando `@Valid` rechaza el body, identificando documento si está presente. Enfoque: handler específico `MethodArgumentNotValidException` en `GlobalExceptionHandler` (exception/) que, si el body contiene documento, llama a `AuditService.registrarRechazo`. Test: POST inválido → audit RECHAZADO.

2. **Secret helm prod (HIGH)** — `helm/.../secret.yaml`: eliminar default `"cambiar"`; usar `required`/`fail` si no se provee; `values-prod.yaml` exige Secret externo. Test: helm template prod sin credencial falla.

3. **PII por perfil (MEDIUM)** — `UserController.consultar`: provisional; agregar proyección por perfil cuando Compliance defina. Por ahora documentar y agregar test placeholder que demuestre campos por perfil (sin filtro real). No bloquea.

4. **Location header 201 (MEDIUM)** — `UserController.alta` ya usa `ResponseEntity.created(...)` con path; verificar que el header salga. Agregar test de header Location.

### PR #3 H0 Canal

5. **Header confiable (HIGH)** — `CredencialCanal`: reemplazar `X-Canal-Credencial-Valida: true` por token firmado/validado por upstream. Arquitectura: introducir `CanalCredentialToken` validado por un `CanalCredentialValidator` (service) que verifica firma/issuer; controller recibe el token en header `X-Canal-Credencial-Token`. Tests de bypass.

6. **Duplicados aceptación términos (HIGH)** — `VinculoService.aceptarTerminos`: rechazar segundo o reusar existente; unicidad en entidad/DB (`@UniqueConstraint` sobre número). Test de duplicado.

### PR #4 H2 Credenciales

7. **Renovación descarta JWT (HIGH)** — `RenovacionService`: entregar JWT renovado a H3 mediante contrato/evento fiable. Arquitectura: publicar evento `CredencialRenovada` (Kafka) con nuevo JWT (o endpoint de entrega); H3 consume y rota. Asegurar que el token en uso no se invalide antes de que H3 obtenga reemplazo (grace period). Tests de entrega.

8. **Autenticar caller emisión (MEDIUM)** — `CredencialController` emisión/revocación: autenticación servicio-a-servicio (mTLS/JWT interno) + autorización por actor (H3 para emitir/revocar, banco para masiva). No confiar en flags del body. Tests de 401/403.

9. **Revocación global sin actor banco (MEDIUM)** — `/revocacion-masiva`: limitar a actor banco. Integrado con punto 8.

10. **Validar body /validar (MEDIUM)** — `CredencialController.validar`: `@Valid` + `credencial` obligatoria no vacía. Test 400 VALIDATION_ERROR.

## Paquetes afectados

- `com.bcra.users.ws...` (H1): exception/, controller/, service/audit
- `com.bcra.channel.ws...` (H3): vinculo/, credencial canal
- `com.bcra.credentials.ws...` (H2): credencial/, renovacion, controller

## Riesgos

- Cambio de contrato de header en H0 afecta H0.2/H9; coordinar.
- Evento de renovación requiere schema en shared/contracts.
- Helm tests requieren helm instalado (no disponible en env actual).

## Verificación

- `mvnw.cmd -q -B verify` por servicio.
- Tests nuevos por punto.
- helm lint (si disponible).
