-- Aplicar una vez a wa_channel antes de arrancar el servicio (ddl-auto=none).
BEGIN;

CREATE TABLE vinculo (
    id UUID PRIMARY KEY,
    numero_whatsapp VARCHAR(16) NOT NULL CHECK (numero_whatsapp ~ '^\+[1-9][0-9]{1,14}$'),
    documento_tipo VARCHAR(255),
    documento_numero VARCHAR(255),
    estado VARCHAR(32) NOT NULL CHECK (estado IN ('PENDIENTE_VERIFICACION', 'ACTIVO', 'BLOQUEADO', 'DESVINCULADO')),
    motivo_bloqueo VARCHAR(32) CHECK (motivo_bloqueo IN
        ('SOLICITUD_CLIENTE', 'POLITICA_BANCO', 'INTENTOS_AGOTADOS')),
    terminos_aceptados BOOLEAN NOT NULL,
    fecha_aceptacion_terminos TIMESTAMP WITH TIME ZONE,
    idioma VARCHAR(255),
    notificaciones_habilitadas BOOLEAN NOT NULL,
    horario_preferido VARCHAR(255),
    fecha_activacion TIMESTAMP WITH TIME ZONE,
    fecha_ultimo_cambio_estado TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uq_vinculo_numero_activo ON vinculo (numero_whatsapp) WHERE estado = 'ACTIVO';
CREATE INDEX ix_vinculo_documento_fecha ON vinculo
    (documento_tipo, documento_numero, fecha_ultimo_cambio_estado DESC);

CREATE TABLE intento_verificacion (
    id UUID PRIMARY KEY,
    vinculo_id UUID NOT NULL CONSTRAINT fk_intento_vinculo REFERENCES vinculo (id),
    fecha TIMESTAMP WITH TIME ZONE NOT NULL,
    resultado VARCHAR(16) NOT NULL CHECK (resultado IN ('EXITOSO', 'FALLIDO'))
);

CREATE INDEX ix_intento_vinculo_resultado_fecha ON intento_verificacion (vinculo_id, resultado, fecha);

COMMIT;

