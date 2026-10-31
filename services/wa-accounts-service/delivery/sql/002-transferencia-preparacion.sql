-- Aplicar sobre el esquema existente de H4 antes de desplegar TASK-47 (ddl-auto=validate).
ALTER TABLE transferencias ADD COLUMN expira_en TIMESTAMP WITH TIME ZONE;
ALTER TABLE transferencias ADD COLUMN concepto VARCHAR(255);

-- Las preparaciones históricas sin vencimiento no son confirmables ni reservan disponibilidad.
UPDATE transferencias SET estado = 'EXPIRADA'
WHERE estado IN ('PREPARADA', 'CONFIRMADA') AND expira_en IS NULL;
