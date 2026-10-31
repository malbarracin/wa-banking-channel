-- Metadatos suficientes para reconstruir la entrega tras un reinicio, sin persistir JWT.
CREATE TABLE entrega_renovacion (
    anterior_id UUID PRIMARY KEY REFERENCES credencial_sesion(id),
    jti VARCHAR(255) NOT NULL UNIQUE,
    fecha_emision TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_expiracion TIMESTAMP WITH TIME ZONE NOT NULL
);
