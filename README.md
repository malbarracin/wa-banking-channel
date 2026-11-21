# wa-banking-channel

Canal bancario de WhatsApp. El repositorio agrupa los servicios y contratos del programa.

## Componentes

| Componente | Descripción | Documentación |
| --- | --- | --- |
| Servicio de Usuarios (`wa-users-service`) | Alta, consulta, actualización de datos permitidos y estados operativos de los clientes del banco. | [README del componente](services/wa-users-service/README.md) · [Contrato](shared/contracts/wa-users-service/) |

## Contratos

Los contratos publicados de cada componente están en [`shared/contracts/`](shared/contracts/).

## Desarrollo

El entorno local del Servicio de Usuarios se levanta desde la raíz con Docker Compose: copiar `.env.example` a `.env`, completar las variables de la base y ejecutar `docker compose up -d --build --wait`. La verificación del componente es `./mvnw verify` desde `services/wa-users-service`. El detalle está en el [README del componente](services/wa-users-service/README.md).
