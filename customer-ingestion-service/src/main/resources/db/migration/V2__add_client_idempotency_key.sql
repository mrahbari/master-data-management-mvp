ALTER TABLE ingestion_idempotency_keys
    ADD COLUMN client_idempotency_key VARCHAR(255);

CREATE INDEX idx_idempotency_client_key ON ingestion_idempotency_keys(client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;
