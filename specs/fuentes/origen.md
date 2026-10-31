# Origen de los requisitos

| Campo | Valor |
|-------|-------|
| Proyecto | wa-banking-channel (mi-proyecto) |
| Fuente | PR reviews + specs existentes (H1/H2/H3) |
| Link | https://github.com/malbarracin/wa-banking-channel/pulls (PR #2, #3, #4) |
| Actualizado | 2026-10-31 |

## Requisito → fuente

| Requisito | Área | Id en la fuente |
|-----------|------|-----------------|
| Alta de usuario bancario (auditoría de rechazos, Location header) | usuarios | PR #2 hilos PRRT_kwDOVOsBA86wTyNc, PRRT_kwDOVOsBA86wTyNu |
| Consulta con proyección/autorización por perfil | usuarios | PR #2 hilo PRRT_kwDOVOsBA86wTyNp |
| Secret Helm sin valor por defecto | usuarios | PR #2 hilo PRRT_kwDOVOsBA86wTyNl |
| Aceptación de términos sin duplicados / unicidad | canal-whatsapp | PR #3 hilo PRRT_kwDOVOsBA86wX1eL |
| No confiar en header cliente | canal-whatsapp | PR #3 hilo PRRT_kwDOVOsBA86wX1eC |
| Renovación entrega token sustituto sin invalidar prematuramente | acceso-sesion | PR #4 hilo PRRT_kwDOVOsBA86wcpmQ |
| Autenticación/autorización servicio-a-servicio y por actor | acceso-sesion | PR #4 hilos PRRT_kwDOVOsBA86wcpmX, PRRT_kwDOVOsBA86wcpmd |
| Validación body /validar con @Valid | acceso-sesion | PR #4 hilo PRRT_kwDOVOsBA86wcpmg |
