# Servicio de Usuarios

Servicio de dominio para la administración de usuarios bancarios.

Compilar y verificar con `./mvnw verify`. Para ejecutar con H2 de desarrollo, usar `./mvnw -Pdev spring-boot:run -Dspring-boot.run.profiles=dev`. Para PostgreSQL, configurar las variables de `.env.example` en el entorno.

El gateway autentica y establece `X-Actor-Id` y `X-Actor-Role`; el servicio solo debe ser accesible a través de ese gateway confiable, que debe eliminar los headers proporcionados por el cliente. Los roles acordados son `OPERACIONES` (todas las operaciones de usuarios), `SOPORTE` (historial) y `SERVICIO_INTERNO` (consulta por id o documento). La API especificada no define una ruta separada de consulta de estado para soporte.
