-- Aplicar una vez si V001 ya estaba instalado sin el índice de pendiente.
-- En instalaciones nuevas, V001 ya incluye este índice: omitir este script.
BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS uq_vinculo_numero_pendiente ON vinculo (numero_whatsapp)
    WHERE estado = 'PENDIENTE_VERIFICACION';

COMMIT;
