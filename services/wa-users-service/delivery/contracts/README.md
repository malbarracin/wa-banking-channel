# Copia revisable del handoff H1

Destino canónico del monorepo: `shared/contracts/wa-users-service/`
(raíz `wa-banking-channel`).

Esta carpeta duplica el contrato de consumo (H3, elegibilidad, errores, ejemplos)
para revisión dentro del servicio si el árbol `shared/` no está en el mismo PR.

Tras `./mvnw -q -B verify`, sincronizar OpenAPI:

```powershell
Copy-Item delivery/openapi/openapi.json,delivery/openapi/openapi.yaml `
  ../../shared/contracts/wa-users-service/ -Force
Copy-Item delivery/openapi/openapi.json,delivery/openapi/openapi.yaml `
  delivery/contracts/ -Force
```

Ver también: `consumer-h3.md`, `examples.md` en el destino canónico.
