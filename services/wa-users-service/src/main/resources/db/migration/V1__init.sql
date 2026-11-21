CREATE TABLE users (
    id UUID PRIMARY KEY,
    tipo_documento VARCHAR(32) NOT NULL,
    numero_documento VARCHAR(64) NOT NULL,
    nombre_completo VARCHAR(255) NOT NULL,
    nombre_tratamiento VARCHAR(255),
    fecha_nacimiento DATE NOT NULL,
    email VARCHAR(255) NOT NULL,
    telefono VARCHAR(64) NOT NULL,
    preferencias VARCHAR(2048),
    estado VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_users_documento UNIQUE (tipo_documento, numero_documento)
);
CREATE INDEX idx_users_estado ON users (estado);

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(32) NOT NULL,
    resultado VARCHAR(32) NOT NULL,
    detalle JSONB NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    actor VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_user ON audit_events (user_id, created_at DESC);
