CREATE TABLE outbox (
    event_id UUID PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL REFERENCES conversation_state(conversation_id),
    correlation_id UUID NOT NULL,
    message_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    topic VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_pending ON outbox(published_at, created_at);
