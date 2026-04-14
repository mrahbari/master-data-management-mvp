-- Transactional Outbox Table
-- Solves the dual-write problem by storing events in the same transaction as business data

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,       -- e.g., 'CUSTOMER'
    aggregate_id    VARCHAR(255) NOT NULL,       -- e.g., customer nationalId
    event_type      VARCHAR(100) NOT NULL,       -- e.g., 'CUSTOMER_CREATED'
    event_version   VARCHAR(20) NOT NULL DEFAULT '1.0',
    payload         JSONB NOT NULL,
    headers         JSONB,
    idempotency_key_hash VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed       BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0,
    last_error      TEXT
);

-- Partial unique index for efficient polling of unprocessed events
-- Note: PostgreSQL requires separate CREATE UNIQUE INDEX for partial unique indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_outbox_events_unprocessed
    ON outbox_events(id) WHERE processed = FALSE;

-- Index for efficient polling of pending events
CREATE INDEX IF NOT EXISTS idx_outbox_events_created
    ON outbox_events(created_at) WHERE processed = FALSE;

-- Index for retry queries
CREATE INDEX IF NOT EXISTS idx_outbox_events_retry
    ON outbox_events(retry_count, created_at)
    WHERE processed = FALSE;

-- Index for aggregate type queries
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate_type
    ON outbox_events(aggregate_type);
