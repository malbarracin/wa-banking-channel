CREATE TABLE saga_step_execution (
    execution_id UUID PRIMARY KEY,
    saga_id UUID NOT NULL REFERENCES saga_instance(saga_id),
    step VARCHAR(64) NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt > 0),
    status VARCHAR(32) NOT NULL CHECK (status IN
        ('PENDING', 'IN_PROGRESS', 'WAITING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED')),
    result_code VARCHAR(64),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_saga_step_attempt UNIQUE (saga_id, step, attempt)
);
