---
name: java-security-owasp
description: >
  Revisión de seguridad para Java / Spring Boot según OWASP Top 10: control de acceso, secretos y criptografía,
  inyección, configuración, dependencias, autenticación, deserialización, logging y SSRF/XXE, con la severidad de cada
  hallazgo. Para revisar código (reviewer de tareas y revisor del pull request).
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  stack: java, spring-boot
  applies-to: "src/main/java/**, src/test/java/**, pom.xml, **/application*.yml"
---

# Seguridad Java (OWASP)

Un hallazgo de esta skill con severidad **HIGH o CRITICAL bloquea la aprobación**.

## Antes de marcar: qué pide la especificación

La seguridad se evalúa contra lo que el servicio **tiene que** hacer. Si la especificación no pide autenticación ni
autorización (ej. un servicio interno detrás de un gateway), la falta de `@PreAuthorize` **no** es HIGH: anotalo como
MEDIUM o pregunta ("¿la autorización la hace el gateway?"). Lo que sí es HIGH siempre: secretos, inyección, datos
sensibles expuestos, configuración insegura en producción.

## A01 — Control de acceso

- Endpoints que modifican (`POST`, `PUT`, `PATCH`, `DELETE`) sin autorización, **cuando la especificación la pide**.
- IDOR: operar por `{id}` sin verificar dueño / tenant, cuando hay dueños.
- `/actuator/**` expuesto sin restricción (más que `health` e `info`).

## A02 — Secretos y criptografía

- Secretos en código, en YAML commiteado o en logs (`password`, `apiKey`, `token`, claves privadas) → CRITICAL.
- Contraseñas con `MD5` / `SHA1` (exigir BCrypt o Argon2).

## A03 — Inyección

- Concatenar input del usuario en queries SQL, JPQL o Mongo; `@Query` sin parámetros → CRITICAL si es explotable.
- SpEL dinámico con input del usuario; rutas de archivo armadas con input (path traversal).

## A04 — Diseño inseguro

- Stack traces o mensajes internos en la respuesta (ver `error-contract`).
- Reglas de negocio críticas solo en el controller (se pueden saltear).

## A05 — Configuración

- `permitAll()` amplio o seguridad desactivada en el perfil `prod`.
- CORS `*` con credenciales.
- Consolas de base de datos (H2) o debug habilitados fuera de `dev`.

## A06 — Dependencias vulnerables

- Versiones fuera del BOM o librerías con CVE conocido (HIGH si es explotable en este servicio, MEDIUM si no).

## A07 — Autenticación

- Login o registro sin validación de entrada; tokens sin expiración; credenciales por defecto en la configuración.

## A08 — Integridad de datos

- Deserialización insegura (`ObjectInputStream`, JSON polimórfico sin restringir tipos).

## A09 — Logging

- Datos personales, tokens o contraseñas en logs (MEDIUM; HIGH si son credenciales).
- Excepciones de seguridad tragadas sin registro.

## A10 — SSRF / XXE

- `RestTemplate` / `WebClient` con URL que controla el usuario sin lista de permitidas.
- Parsers XML sin `FEATURE_SECURE_PROCESSING`.

## Severidad

| Hallazgo | Severidad |
|----------|-----------|
| Secretos en el repo, inyección explotable | CRITICAL |
| Endpoint que modifica sin autorización (si la spec la pide), IDOR, actuator abierto, CORS permisivo, CVE alto | HIGH |
| Datos personales en logs, falta de autorización cuando la spec no la pide | MEDIUM |

## Checklist

- [ ] Sin secretos en código, configuración ni logs
- [ ] Sin inyección (queries parametrizadas, sin SpEL ni rutas con input del usuario)
- [ ] Autorización donde la especificación la pide; actuator restringido
- [ ] Sin stack traces ni datos internos en las respuestas
- [ ] Configuración de `prod` sin consolas, debug ni CORS abierto
- [ ] Dependencias del BOM, sin CVE conocidos explotables
