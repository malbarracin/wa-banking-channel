CREATE TABLE verification_attempts (
    id UUID NOT NULL PRIMARY KEY,
    binding_id UUID NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    success BOOLEAN NOT NULL,
    CONSTRAINT fk_verification_attempts_binding
        FOREIGN KEY (binding_id) REFERENCES bindings (id)
);

CREATE INDEX idx_verification_attempts_binding ON verification_attempts (binding_id);
