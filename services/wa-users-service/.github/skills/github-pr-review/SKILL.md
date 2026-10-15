---
name: github-pr-review
description: >
  Revisión del pull request en GitHub (Java / Spring Boot): lee el diff completo con gh, revisa seguridad, arquitectura,
  calidad y tests contra las skills y reglas del equipo, publica comentarios en las líneas y el veredicto, y responde
  ok o changes_requested para que el equipo corrija solo.
license: MIT
compatibility: opencode, claude-code, codex, cursor, kiro
metadata:
  audience: pr-code-reviewer
  stack: java, spring-boot
---

# Revisión del pull request

## 1. El PR

El link lo da el paso ("Ya entregado por pr-publisher: …/pull/N").

```bash
gh auth status
gh pr view <N> --json number,url,title,state,baseRefName,headRefName,headRefOid,files
```

PR cerrado o mergeado, o sin `gh` / sin sesión → **bloqueado** con el paso para el usuario.

## 2. El diff completo

```bash
gh pr diff <N>
```

Leé **cada archivo** (no resumas sin leer). Foco: `src/main/java/**`, `src/test/java/**`, `pom.xml`,
`application*.yml`, `Dockerfile`, `docker-compose*.yml`. Para entender el contexto podés leer los archivos del
proyecto (en la carpeta de trabajo, que es la misma rama), sin editarlos.

**Segunda revisión** (el equipo ya corrigió lo que pediste): mirá solo lo que cambió desde tu revisión anterior
(`gh pr view <N> --json commits`, los commits nuevos) y confirmá que cada hallazgo anterior quedó resuelto.

## 3. Qué revisar

| Tema | Contra qué |
|------|-----------|
| Seguridad | skill `java-security-owasp` |
| Arquitectura y capas | regla `architecture.md` (paquetes por módulo y capa) |
| Calidad de código | skills `java-best-practices` y `java-code-style` |
| Errores HTTP | skill `error-contract` |
| Versionado de la API | skill `api-versioning` |
| Dependencias / `pom.xml` | skill `maven-dependency-hygiene` |
| Docker / config | skill `docker-support` (si el PR trae Dockerfile o compose) |

Además, lo que se ve recién con el PR completo:

- **REST**: recursos en plural, verbos correctos, `201` + `Location` al crear, `204` al borrar sin cuerpo, `@Valid`
  en cada `@RequestBody`.
- **Capas**: entidad devuelta directo por un controller, `@Transactional` en el controller, lógica de negocio en el
  controller, controller que usa el repository directo → HIGH.
- **SOLID en la práctica**: service gigante con varias responsabilidades, cadenas de `if/else` por tipo, dependencias
  circulares, estado mutable compartido en un bean → HIGH si afecta corrección, MEDIUM si es mantenibilidad.
- **Tests**: tests sin asserts reales, que dependen del orden o del tiempo (comparar instantes que pueden ser
  iguales), código nuevo de riesgo sin test → HIGH.
- **Basura en el PR**: `target/`, `.env`, logs o archivos generados en el diff → HIGH.
- **Consistencia**: lo que dice el README, Swagger y las colecciones coincide con los endpoints reales.

Severidades y veredicto: regla `pr-review-verdict`.

## 4. Publicar en GitHub

Regla `pr-review-workflow`: comentarios en las líneas (obligatorio para CRITICAL/HIGH) y el veredicto.

## 5. Responder

Regla `pr-review-output-format`. `RESULTADO: changes_requested` lleva la lista de hallazgos bloqueantes con archivo,
línea y arreglo: es lo que recibe el desarrollador para corregir.

## Checklist

- [ ] Diff completo leído (todos los archivos `.java`, tests y config)
- [ ] Seguridad (`java-security-owasp`) y estándares del equipo revisados
- [ ] Cada CRITICAL/HIGH con comentario en su línea del PR
- [ ] Review publicado en GitHub (o como comentario si el PR es de tu misma cuenta)
- [ ] Sin aprobar con CRITICAL/HIGH abiertos
- [ ] Respuesta con veredicto, tabla de hallazgos y `RESULTADO: ok` o `changes_requested`
