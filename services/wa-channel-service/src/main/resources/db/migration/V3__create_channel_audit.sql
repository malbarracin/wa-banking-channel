CREATE TABLE channel_audit_entries (
    id UUID NOT NULL PRIMARY KEY,
    binding_id UUID NOT NULL,
    whatsapp_number VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    detail VARCHAR(512),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_channel_audit_whatsapp_occurred
    ON channel_audit_entries (whatsapp_number, occurred_at DESC);
