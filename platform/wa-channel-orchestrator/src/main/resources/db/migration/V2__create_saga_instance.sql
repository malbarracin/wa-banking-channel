CREATE TABLE saga_instance (
    saga_id UUID PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL REFERENCES conversation_state(conversation_id),
    type VARCHAR(32) NOT NULL CHECK (type IN ('ONBOARDING', 'REVERIFICATION')),
    status VARCHAR(32) NOT NULL CHECK (status IN
        ('PENDING', 'IN_PROGRESS', 'WAITING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED')),
    current_step VARCHAR(64) NOT NULL,
    context JSONB NOT NULL,
    timeout_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_saga_conversation ON saga_instance(conversation_id);
CREATE INDEX idx_saga_timeout ON saga_instance(status, timeout_at);
