CREATE TABLE conversation_state (
    conversation_id VARCHAR(128) PRIMARY KEY,
    correlation_id UUID NOT NULL,
    state VARCHAR(64) NOT NULL,
    last_message_id VARCHAR(128),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
