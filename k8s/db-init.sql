-- Database initialization script for MDM MVP
-- Run this on PostgreSQL after deployment
--
-- nationalId is the canonical unique identifier for customer deduplication.
-- All events for the same nationalId are guaranteed to arrive in order via Kafka partitioning.

-- Raw customer events (immutable audit trail)
CREATE TABLE IF NOT EXISTS customer_raw (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL UNIQUE,
    national_id     VARCHAR(64) NOT NULL,
    name            VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(64),
    source_system   VARCHAR(50) NOT NULL,
    raw_payload     JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_raw_national_id ON customer_raw(national_id);
CREATE INDEX IF NOT EXISTS idx_customer_raw_event_id ON customer_raw(event_id);
CREATE INDEX IF NOT EXISTS idx_customer_raw_created ON customer_raw(created_at);

-- Golden record (single source of truth)
CREATE TABLE IF NOT EXISTS customer_golden (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    national_id         VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(64),

    -- Trust scoring (simple MVP version)
    confidence_score    SMALLINT NOT NULL DEFAULT 100,

    -- Audit
    version             BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_source_system  VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_customer_golden_national_id ON customer_golden(national_id);
CREATE INDEX IF NOT EXISTS idx_customer_golden_updated ON customer_golden(updated_at);
