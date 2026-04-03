-- Database initialization script for MDM MVP
-- Run this on PostgreSQL after deployment

-- Raw customer events (immutable audit trail)
CREATE TABLE IF NOT EXISTS customer_raw (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL,
    first_name      VARCHAR(100),
    last_name       VARCHAR(100),
    phone           VARCHAR(20),
    source_system   VARCHAR(50) NOT NULL,
    raw_payload     JSONB NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_raw_email ON customer_raw(email);
CREATE INDEX IF NOT EXISTS idx_customer_raw_created ON customer_raw(created_at);

-- Golden record (single source of truth)
CREATE TABLE IF NOT EXISTS customer_golden (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_email    VARCHAR(255) NOT NULL UNIQUE,
    email               VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    phone               VARCHAR(20),
    
    -- Trust scoring (simple MVP version)
    confidence_score    SMALLINT NOT NULL DEFAULT 100,
    
    -- Audit
    version             BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_source_system  VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_customer_golden_email ON customer_golden(normalized_email);
CREATE INDEX IF NOT EXISTS idx_customer_golden_updated ON customer_golden(updated_at);
