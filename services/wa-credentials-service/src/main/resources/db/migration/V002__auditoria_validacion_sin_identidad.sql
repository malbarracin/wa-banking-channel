-- Un JWT sin firma válida no permite atribuir el intento a un número confiable.
ALTER TABLE credencial_audit_log ALTER COLUMN numero_whatsapp DROP NOT NULL;
