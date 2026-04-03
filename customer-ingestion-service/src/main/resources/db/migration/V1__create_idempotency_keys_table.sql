CREATE TABLE ingestion_idempotency_keys (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash        VARCHAR(64) NOT NULL UNIQUE,
    event_id        UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    response_body   JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours')
);

CREATE INDEX idx_idempotency_expires ON ingestion_idempotency_keys(expires_at);
