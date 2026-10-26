# Esquema del vínculo

Aplicar una sola vez, antes de arrancar el servicio, sobre su base propia:

```shell
psql "$DB_URL" -v ON_ERROR_STOP=1 -f V001__vinculo.sql
# Solo si la base ya tenía V001 sin el índice de pendiente:
psql "$DB_URL" -v ON_ERROR_STOP=1 -f V002__vinculo_numero_pendiente_unico.sql
```

DB_URL debe ser una URL de conexión de psql (postgresql://...), sin el prefijo JDBC.
El servicio conserva ddl-auto=none: este script se aplica explícitamente, no por Hibernate.
V001 crea las tablas, la FK local y los índices únicos parciales para números activos y
pendientes de verificación (como máximo uno de cada uno por número; historial BLOQUEADO/DESVINCULADO libre).
V002 es incremental para bases que ya tenían V001 antiguo.
Los documentos son referencias externas a H1, sin FK física.

Los tests @DataJpaTest usan H2 con esquema generado por Hibernate y verifican persistencia,
FK y consultas. El índice parcial y el CHECK SQL de E.164 requieren PostgreSQL;
H2 no reproduce esas características del esquema de producción.
