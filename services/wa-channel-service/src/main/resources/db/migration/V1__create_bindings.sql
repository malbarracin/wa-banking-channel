CREATE TABLE bindings (
    id UUID NOT NULL PRIMARY KEY,
    whatsapp_number VARCHAR(32) NOT NULL,
    client_id UUID,
    status VARCHAR(32) NOT NULL,
    terms_accepted_at TIMESTAMP WITH TIME ZONE,
    verified_at TIMESTAMP WITH TIME ZONE,
    language_preference VARCHAR(16) NOT NULL DEFAULT 'es',
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    actor VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_bindings_whatsapp_number ON bindings (whatsapp_number);
