#!/bin/bash
set -e

# Create additional databases for MDM MVP
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE mdm_ingestion;
    GRANT ALL PRIVILEGES ON DATABASE mdm_ingestion TO $POSTGRES_USER;
EOSQL

echo "✅ Created database: mdm_ingestion"
