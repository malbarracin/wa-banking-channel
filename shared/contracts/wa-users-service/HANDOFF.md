# Handoff — wa-users-service (H1)

Contrato publicable para consumidores del banking channel (H2/H3/H4+).

**Contacto:** Ingenia — marcelo.albarracin@ingenia.la

---

## Paths

| Artefacto | Path en monorepo |
|-----------|------------------|
| OpenAPI (publicado) | `shared/contracts/wa-users-service/openapi.yaml` |
| Handoff (este archivo) | `shared/contracts/wa-users-service/HANDOFF.md` |
| OpenAPI (fuente servicio) | `services/wa-users-service/docs/openapi.yaml` |
| Postman | `services/wa-users-service/docs/postman/wa-users-service.postman_collection.json` |
| Runbook / curls | `services/wa-users-service/README.md` |
| Bundle release | `services/wa-users-service/backlog/exports/h1-servicio-de-usuarios/` |

## Runtime (live docs)

| Recurso | URL local |
|---------|-----------|
| Base URL | `http://localhost:8080` |
| API v1 | `http://localhost:8080/api/v1/users` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Health | `http://localhost:8080/actuator/health` |

---

## Integración H3 — existencia de cliente + estado

Antes de vincular WhatsApp / canal:

1. Consultar usuario por `id` o por `(documentType, documentNumber)`.
2. Si **404** → no existe → no vincular.
3. Si **200** y `channelLinkEligible == false` → **no vincular**.
4. Si **200** y `channelLinkEligible == true` → elegible.

```
channelLinkEligible === true  ⟺  status === ACTIVE
```

| `status` | `channelLinkEligible` | Vínculo canal |
|----------|----------------------|---------------|
| `ACTIVE` | `true` | Permitido |
| `SUSPENDED` | `false` | **Prohibido** |
| `LOGICALLY_DELETED` | `false` | **Prohibido** |

H1 no emite eventos a H3: reconsultar estado / elegibilidad.

---

## Alcance API v1

- Base path: `/api/v1/users`
- U1 `POST /` — alta (unicidad documento) → `ACTIVE`
- U2 `GET /{id}` — consulta por id
- U2 `GET /?documentType=&documentNumber=` — consulta por documento
- U3 `PATCH /{id}` — actualización parcial (`preferredName`, `email`, `phone`, `preferredLanguage`)
- U4 `PATCH /{id}/status` — cambio de estado
- `GET /{id}/audit` — auditoría (`USER_CREATED` \| `USER_UPDATED` \| `STATUS_CHANGED`)

### Campos consultables (`UserResponse`)

`id`, `documentType`, `documentNumber`, `firstName`, `lastName`, `email`, `phone`,
`preferredName`, `preferredLanguage`, `status`, `channelLinkEligible`, `createdAt`, `updatedAt`

**No se exponen:** domicilio legal, estado civil, WhatsApp, tokens, productos.

### Reglas de negocio

- Unicidad: `(documentType, documentNumber)` → duplicado HTTP **409**
- Estados: `ACTIVE` \| `SUSPENDED` \| `LOGICALLY_DELETED`
- Sin JWT / sin auth
- Sin WhatsApp / sin vínculo de canal (responsabilidad H3)
- Header opcional de auditoría: `X-Actor-Id`

### Transiciones de estado

```
CREATE → ACTIVE
ACTIVE ↔ SUSPENDED
ACTIVE | SUSPENDED → LOGICALLY_DELETED
LOGICALLY_DELETED → ACTIVE
```

Otras → `400 BAD_REQUEST`.

---

## Error contract

```json
{
  "code": "NOT_FOUND | VALIDATION_ERROR | BAD_REQUEST | INTERNAL_ERROR",
  "message": "string",
  "details": [],
  "timestamp": "ISO-8601"
}
```

| HTTP | code | Caso |
|------|------|------|
| 400 | VALIDATION_ERROR | Bean Validation |
| 400 | BAD_REQUEST | Transición inválida / update vacío |
| 404 | NOT_FOUND | Usuario inexistente |
| 409 | BAD_REQUEST | Documento duplicado |
| 500 | INTERNAL_ERROR | Error no controlado |

---

## Release / sync

Este directorio debe permanecer versionado y alineado con
`services/wa-users-service/docs/openapi.yaml`.

Si se regenera el YAML en el servicio:

```bash
cp services/wa-users-service/docs/openapi.yaml shared/contracts/wa-users-service/openapi.yaml
```
