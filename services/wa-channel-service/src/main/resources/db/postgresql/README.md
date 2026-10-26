# Esquema del vínculo

Aplicar una sola vez, antes de arrancar el servicio, sobre su base propia:

```shell
psql "$DB_URL" -v ON_ERROR_STOP=1 -f V001__vinculo.sql
```

DB_URL debe ser una URL de conexión de psql (postgresql://...), sin el prefijo JDBC.
El servicio conserva ddl-auto=none: este script se aplica explícitamente, no por Hibernate.
El script crea las tablas, la FK local y el índice único parcial para números activos.
Los documentos son referencias externas a H1, sin FK física.

Los tests @DataJpaTest usan H2 con esquema generado por Hibernate y verifican persistencia,
FK y consultas. El índice parcial y el CHECK SQL de E.164 requieren PostgreSQL;
H2 no reproduce esas características del esquema de producción.
