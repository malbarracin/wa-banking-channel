# ADR-002: Kafka KRaft para entorno local

- **Estado**: Aceptado
- **Fecha**: 2026-10-31
- **Contexto**: Plataforma de eventos H0.1 usa Kafka KRaft (sin ZooKeeper). Gateway/orquestador lo consumen.
- **Decisión**: Usar broker Apache Kafka 3.9.1 en modo KRaft en el compose raíz, compartido por todos los componentes que lo necesiten.
- **Consecuencias**: Menor complejidad (sin ZK), consistente con stack. Un solo broker para desarrollo local.
- **Alternativas consideradas**: Confluent local, Redpanda — fuera de stack aprobado.
