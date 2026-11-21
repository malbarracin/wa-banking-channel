# Confianza del gateway/IAM

El gateway/IAM autentica al actor y emite un JWT firmado. El consumidor envía `Authorization: Bearer <token>`. El servicio valida la firma con las claves públicas de JWKS y comprueba `iss` contra el emisor configurado, `aud` igual a `wa-users-service`, `exp`, `iat` y `jti`. El claim `sub` identifica al actor y `role` contiene uno de `OPERACIONES`, `SOPORTE` o `SERVICIO_INTERNO`. La identidad y el rol de auditoría proceden exclusivamente de esos claims; los encabezados `X-Actor-Id` y `X-Actor-Role` recibidos no tienen autoridad.

Configurar `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` (propiedad `app.gateway.jwt.jwk-set-uri`) con el endpoint JWKS y `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` (propiedad `app.gateway.jwt.issuer`) con el emisor esperado. Ambos son obligatorios en producción; deben provenir de la configuración de despliegue de confianza. Usar HTTPS para JWKS en producción.

Un token ausente o inválido devuelve 401 `INVALID_TOKEN`; uno vencido devuelve 401 `TOKEN_EXPIRED`. Claims de identidad o rol insuficientes y roles sin permiso devuelven 403 `INSUFFICIENT_CLAIMS`. Las respuestas conservan el esquema de error del servicio. Los tokens no se registran en logs.
