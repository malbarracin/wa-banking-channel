CREATE TABLE message_dedupe (
    id UUID PRIMARY KEY,
    message_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(128) NOT NULL,
    correlation_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_message_dedupe_message_id UNIQUE (message_id)
);
CREATE INDEX idx_dedupe_processed_at ON message_dedupe(processed_at);
