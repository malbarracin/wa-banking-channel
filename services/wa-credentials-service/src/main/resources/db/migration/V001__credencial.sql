CREATE TABLE credencial_sesion (
    id UUID PRIMARY KEY,
    jti VARCHAR(255) NOT NULL UNIQUE,
    numero_whatsapp VARCHAR(20) NOT NULL,
    documento_tipo VARCHAR(30) NOT NULL,
    documento_numero VARCHAR(50) NOT NULL,
    vinculo_id UUID NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ACTIVA', 'REVOCADA', 'EXPIRADA')),
    fecha_emision TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_expiracion TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_revocacion TIMESTAMP WITH TIME ZONE,
    motivo_revocacion VARCHAR(30) CHECK (motivo_revocacion IN
        ('BLOQUEO', 'DESVINCULACION', 'FRAUDE', 'POLITICA_BANCO', 'REVOCACION_MASIVA', 'REEMPLAZO')),
    riesgo BOOLEAN NOT NULL,
    revocada_por VARCHAR(20) CHECK (revocada_por IN ('CANAL', 'BANCO', 'SISTEMA')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_credencial_numero_activa ON credencial_sesion (numero_whatsapp)
    WHERE estado = 'ACTIVA';
CREATE INDEX ix_credencial_documento ON credencial_sesion (documento_tipo, documento_numero);

-- Sin FK: los intentos rechazados también deben poder registrarse.
CREATE TABLE credencial_audit_log (
    id UUID PRIMARY KEY,
    accion VARCHAR(30) NOT NULL CHECK (accion IN ('EMISION', 'RENOVACION', 'REVOCACION', 'VALIDACION_FALLIDA')),
    resultado VARCHAR(20) NOT NULL CHECK (resultado IN ('EXITOSO', 'RECHAZADO')),
    numero_whatsapp VARCHAR(20) NOT NULL,
    documento_tipo VARCHAR(30),
    documento_numero VARCHAR(50),
    motivo VARCHAR(255),
    jti VARCHAR(255),
    fecha TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_credencial_audit_numero_fecha ON credencial_audit_log (numero_whatsapp, fecha DESC);

