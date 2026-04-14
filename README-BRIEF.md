# Master Data Management (MDM) Platform — Event-Driven Microservices

> **Enterprise-grade customer data unification system** built with Spring Boot 3.2, Kafka, and PostgreSQL, implementing SRE observability practices, CQRS pattern, and advanced conflict resolution for multi-source data consolidation.

---

## 🎯 Project Overview

This platform solves a critical challenge in fintech and high-transaction environments: **creating a single, authoritative source of truth for customer data** arriving from multiple channels (web, mobile, partners, banking systems, government registries).

### Core Problem Solved

In environments like **sportsbook platforms, payment processors, and regulated financial services**, customer data arrives from disparate sources leading to:
- **Duplicate records** — same customer, different representations
- **Inconsistent data** — conflicting values across systems
- **No unified view** — inability to enforce responsible gambling limits, detect bonus abuse, or provide unified reporting

This system implements **deterministic entity resolution** using canonical identifiers, with configurable conflict resolution, full audit lineage, and real-time observability.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        KUBERNETES CLUSTER                               │
│                                                                         │
│  ┌──────────────────────┐         ┌──────────────────────────────────┐  │
│  │  Customer Ingestion  │         │    Customer Mastering Service    │  │
│  │       Service        │         │                                  │  │
│  │  (Command Side)      │         │  ┌────────────────────────────┐  │  │
│  │                      │         │  │   Kafka Consumer           │  │  │
│  │  POST /customers ────┼─Kafka──▶│   (customer.raw)              │  │  │
│  │  202 Accepted        │  Topic  │                                │  │  │
│  │                      │         │  ┌────────────────────────────┐  │  │
│  │  Dual-Key            │         │  │   Golden Record Service    │  │  │
│  │  Idempotency         │         │   │   - Canonical ID dedup     │  │  │
│  │  (Client + SHA-256)  │         │   │   - Conflict Resolution    │  │  │
│  │                      │         │   │   - Create/Update Master   │  │  │
│  │  Kafka Producer      │         │   └────────────┬───────────────┘  │  │
│  │  key=nationalId      │         │                │                  │  │
│  └──────────────────────┘         │                ▼                  │  │
│                                   │        ┌────────────────────┐     │  │
│                                   │        │   PostgreSQL       │     │  │
│                                   │        │   - customer_raw   │     │  │
│                                   │        │   (audit trail)    │     │  │
│                                   │        │   - customer_golden│     │  │
│                                   │        │   (golden record)  │     │  │
│                                   │        └────────────────────┘     │  │
│                                   └──────────────────────────────────┘  │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                     Kafka (KRaft Mode)                           │   │
│  │   Topics: customer.raw, customer.mastered                        │   │
│  │   Partition Key: nationalId → per-entity ordering guaranteed     │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### Design Pattern: CQRS (Command Query Responsibility Segregation)

| Component | Responsibility | Port |
|-----------|----------------|------|
| **Customer Ingestion Service** | Command side — accepts writes, validates, publishes events | `8080` |
| **Customer Mastering Service** | Query side — consumes events, maintains golden records, serves reads | `8081` |
| **Kafka** | Event backbone — durable, replayable, partitioned by canonical ID | `9092` |
| **PostgreSQL** | Dual-table storage — immutable raw events + curated golden records | `5432` |

---

## 🔬 Advanced Engineering Concepts

### 1. SRE Observability Framework

This system implements **Google SRE practices** with comprehensive SLI/SLO tracking:

#### Service Level Indicators (SLIs)

| SLI ID | Metric | SLO Target | Implementation |
|--------|--------|------------|----------------|
| **SLI-001** | Event Processing Latency | p99 < 100ms | `Timer` with percentile histogram (10ms–1s buckets) |
| **SLI-002** | Deduplication Lookup Latency | p99 < 10ms | `Timer` with percentile histogram (1ms–50ms buckets) |
| **SLI-003** | Processing Error Rate | < 0.1% | `Counter` tracking failures vs total |
| **SLI-004** | Duplicate Detection Rate | 20–40% expected | `Counter` with anomaly detection |
| **SLI-005** | Throughput | > 1000 events/sec | `FunctionCounter` via `AtomicLong` |

#### Service Level Objectives (SLOs)

```
SLO Target: 99.9% Availability
├── Monthly Error Budget: 43.2 minutes
├── Weekly Error Budget: 10.08 minutes
└── Daily Error Budget: 1.44 minutes

Burn Rate Severity Levels:
├── CRITICAL: ≥10x burn rate → Immediate action required
├── HIGH: ≥5x burn rate → Urgent attention needed
├── ELEVATED: ≥2x burn rate → Monitor closely
└── NORMAL: <2x burn rate → Operations within SLO
```

#### Custom Actuator Endpoint: `/actuator/slo-status`

```json
{
  "availability": "99.9500%",
  "errorBudgetRemaining": "67.50%",
  "burnRate": "1.20x",
  "burnRateSeverity": "NORMAL",
  "burnRateDescription": "Normal operations",
  "monthlyErrorBudgetMinutes": 43.2,
  "weeklyErrorBudgetMinutes": 10.08,
  "dailyErrorBudgetMinutes": 1.44,
  "maxAllowedErrorRate": "0.1000%",
  "sloMet": true,
  "status": "HEALTHY",
  "totalEventsProcessed": 15420,
  "totalErrors": 8,
  "duplicateRate": "27.35%",
  "sloTarget": "99.90%",
  "timestamp": "2026-04-07T15:30:00Z"
}
```

#### Custom Health Indicators

| Health Indicator | Monitors | Alert Conditions |
|------------------|----------|------------------|
| **DatabaseHealthIndicator** | Connection pool, query latency | Connection failures, slow queries |
| **KafkaHealthIndicator** | Broker connectivity, produce latency | Broker unreachable, produce > threshold |
| **MdmProcessingHealthIndicator** | Processing recency, duplicate rate anomalies | No processing in 5 min, duplicate rate <5% or >95% |

---

### 2. Dual-Key Idempotency Strategy

Prevents duplicate event processing across distributed microservices with a **two-tier strategy**:

```
Client Request
  │
  ├── X-Idempotency-Key header? ──YES──▶ Check by client key (SHA-256 hashed)
  │                                            │
  │                                     Found? ──YES──▶ Check status
  │                                     │               │
  │                                     │               ├── COMPLETED → HIT (return cached response)
  │                                     │               ├── PROCESSING → 409 Conflict (retry later)
  │                                     │               └── EXPIRED → Treat as new request
  │                                     │
  │                                     NO (new unique key)
  │                                      │
  NO                                     ▼
  │                               Check deterministic key
  │                               SHA-256(nationalId | sourceSystem)
  │                                    │
  │                             Found? ──YES──▶ Same status check
  │                             NO
  │                              │
  └──────────────────────────────▼
                          Insert new record (PROCESSING)
                          Atomic: ON CONFLICT DO NOTHING
```

**Key Properties:**
- **Client-provided key**: 24-hour TTL — clients control retry semantics
- **Deterministic key**: 6-hour TTL — automatic payload-based deduplication
- **Pessimistic locking**: `SELECT ... FOR UPDATE` prevents race conditions
- **Sealed result types**: `IdempotencyHit`, `IdempotencyMiss`, `IdempotencyProcessing`, `IdempotencyExpired`

---

### 3. Kafka Partitioning Strategy

**Canonical Key Routing**: `nationalId.toLowerCase().trim()`

| Property | Value | Rationale |
|----------|-------|-----------|
| **Partition Key** | `nationalId` (normalized) | Same customer → same partition → strict ordering |
| **Topic: customer.raw** | 3 partitions, 7-day retention | Ingestion events from all sources |
| **Topic: customer.mastered** | 3 partitions, 30-day retention | Golden record changes (downstream consumers) |
| **Event Headers** | `X-Event-ID`, `X-Idempotency-Key`, `X-Source-System`, `X-Timestamp`, `X-Event-Version` | Tracing, deduplication, versioning |

**Why This Matters:**
- Guarantees per-customer event ordering — critical for deduplication correctness
- Enables horizontal scaling — add partitions as throughput requirements grow
- Supports replayability — reprocess events from any offset for debugging or migration

---

### 4. Configurable Conflict Resolution Engine

When the same customer record arrives from multiple sources with conflicting data, the system applies **per-field resolution strategies**:

| Field | Strategy | Behavior |
|-------|----------|----------|
| `name` | **TRUSTED_SOURCE** | Prefers CRM/Government over self-reported |
| `email` | **LATEST_UPDATE** | Most recent timestamp wins |
| `phone` | **MERGE (UNION)** | Combines values, deduplicates, max 5, FIFO eviction |
| `nationalId` | **TRUSTED_SOURCE** | BANK or GOVERNMENT always wins |
| *other fields* | **LATEST_UPDATE** | Default: most recent wins |

**Supported Strategies:**
- `LATEST_UPDATE` — Most recent timestamp wins
- `TRUSTED_SOURCE` — Prefer values from configured trusted source systems
- `MOST_FREQUENT` — Keep historically observed value (stable attributes)
- `NON_NULL` — Keep first non-null value
- `MERGE` — Combine values (UNION for dedup, APPEND for accumulation)

**Audit Logging:**
Every conflict is logged as structured JSON to `logs/conflict-resolution.log`:
```json
{
  "nationalId": "****56789012",
  "field": "name",
  "currentValue": "John Doe",
  "incomingValue": "Johnny Doe",
  "currentSource": "crm-system",
  "incomingSource": "web-portal",
  "strategy": "TRUSTED_SOURCE",
  "resolvedValue": "John Doe",
  "reason": "Trusted source 'crm-system' preferred over 'web-portal'"
}
```

---

### 5. Data Model & CQRS Storage

**Immutable Audit Trail (`customer_raw`):**
```sql
CREATE TABLE customer_raw (
    id              UUID PRIMARY KEY,
    event_id        UUID NOT NULL UNIQUE,      -- Kafka event UUID
    national_id     VARCHAR(64) NOT NULL,      -- Canonical identifier
    name            VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(64),
    source_system   VARCHAR(50) NOT NULL,      -- Origin system
    raw_payload     JSONB NOT NULL,            -- Full original payload
    created_at      TIMESTAMPTZ NOT NULL
);
```

**Curated Golden Record (`customer_golden`):**
```sql
CREATE TABLE customer_golden (
    id                  UUID PRIMARY KEY,
    national_id         VARCHAR(64) UNIQUE,    -- Canonical unique constraint
    name                VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(64),           -- JSON array: ["+1-555-1234", ...]
    confidence_score    SMALLINT DEFAULT 100,  -- Match confidence
    version             BIGINT DEFAULT 1,      -- Optimistic locking
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    last_source_system  VARCHAR(50)
);
```

---

### 6. Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `mdm.event_processing_latency_seconds` | Timer | End-to-end processing time (SLO: 99% < 100ms) |
| `mdm.deduplication_lookup_latency_seconds` | Timer | DB lookup time (SLO: 99% < 10ms) |
| `mdm.events_processed_total` | Counter | Total throughput |
| `mdm.duplicates_detected_total` | Counter | Duplicate customer records found |
| `mdm.golden_records_created_total` | Counter | New golden records |
| `mdm.golden_records_updated_total` | Counter | Updated golden records |
| `mdm.processing_errors_total` | Counter | Processing failures (SLO: < 0.1%) |
| `conflicts_resolved_total` | Counter | Field-level conflicts resolved |

**JVM & Infrastructure Metrics:**
- GC pauses, heap usage, thread counts, CPU utilization
- Kafka produce latency, bootstrap server connectivity
- PostgreSQL connection pool status, query validation time

---

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 21+ (optional, for local development)

### Start All Services

```bash
docker compose down
docker compose up --build
```

## 📊 System Behavior

### Event Flow

```
1. POST /api/customers
   │
2. Ingestion Service:
   ├─ Sanitize & validate input
   ├─ Resolve idempotency (dual-key)
   │   └─ HIT → 200 OK (cached)
   │   └─ MISS → Continue
   ├─ Build CustomerRawEvent
   ├─ Publish to Kafka (key=nationalId)
   └─ Return 202 Accepted
   │
3. Kafka Topic (customer.raw)
   └─ Routed to partition by nationalId hash
   │
4. Mastering Service (Consumer):
   ├─ Consume event
   ├─ Idempotency check (skip if already processed)
   ├─ Store in customer_raw (audit trail)
   ├─ Lookup by nationalId
   │   ├─ NO MATCH → Create new golden record
   │   └─ MATCH → Merge with conflict resolution
   ├─ Publish to customer.mastered
   └─ Update health indicator & SLI metrics
```

---

## 🛠️ Technology Stack

| Category | Technology |
|----------|------------|
| **Framework** | Spring Boot 3.2, Java 21 |
| **Event Streaming** | Apache Kafka (KRaft mode, no Zookeeper) |
| **Database** | PostgreSQL 15 with JSONB |
| **Database Migration** | Flyway (versioned schema management) |
| **Observability** | Micrometer, Prometheus, Spring Boot Actuator |
| **Authentication** | OAuth2 Resource Server, JWT (RS256) |
| **Containerization** | Docker, Docker Compose, Multi-stage builds |
| **Orchestration** | Kubernetes (Deployments, Services, ConfigMaps, Secrets) |
| **Build Tool** | Gradle 8.5 |
| **Code Quality** | Checkstyle, SpotBugs, Spotless, OWASP Dependency Check |

---

## 🔒 Security

- **OAuth2 Resource Server** with JWT validation (RS256)
- **Role-based access control**: `ADMIN`, `CUSTOMER_WRITE`, `CUSTOMER_READ`
- **Sensitive data masking**: SHA-256 hashed idempotency keys, partial national ID masking in logs
- **Non-root container execution**: Docker images run as dedicated `appuser`
- **Pessimistic locking**: `SELECT ... FOR UPDATE` prevents concurrent idempotency races

---

## 📐 Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Single Responsibility** | Each component has one reason to change: `IngestionInputSanitizer`, `IngestionEventBuilder`, `IdempotencyService`, `CustomerKafkaProducer` |
| **Open/Closed** | Sealed `IdempotencyResult` interface — new types without modifying existing code |
| **Dependency Inversion** | `Clock` injection for deterministic testability, repository interfaces |
| **Immutable Entities** | No public setters — state mutations via domain methods (`markCompleted()`, `markFailed()`) |
| **CQRS** | Separate command (ingestion) and query (mastering) services |
| **Event Sourcing** | Immutable `customer_raw` audit trail, replayable Kafka events |

---

## 🧪 Testing

| Test Category | Coverage |
|---------------|----------|
| **Unit Tests** | Service logic, conflict resolution, idempotency workflow |
| **Integration Tests** | Kafka consumer/producer, database operations, Flyway migrations |
| **Health Indicator Tests** | Processing recency, duplicate rate anomaly detection |
| **SLI Metrics Tests** | Timer recording, counter increments, rate calculations |
| **Retry & DLQ Tests** | Poison message handling, dead letter queue routing |
| **Survivable Matching Tests** | Phonetic matching, Levenshtein distance, nickname mapping |

---

## 🗺️ ROADMAP

Implementation tasks organized by priority. Track progress by updating the **Status** column
(`⬜ Todo` → `🔧 In Progress` → `✅ Done`).

### P0 — Critical (Must Have)

| # | Task | Status |
|---|------|--------|
| 000 | Code Quality Enhancements (SOLID review) | ✅ Done |
| 001 | Idempotent Ingestion Endpoint | ✅ Done |
| 002 | Out-of-Order Event Handling | ⬜ Todo |
| 003 | Conflict Resolution Strategy | ✅ Done |
| 004 | Event Publishing with Change Detection | ⬜ Todo |
| 005 | Retry and Dead Letter Queue | ✅ Done |
| 006 | Duplicate Detection and Entity Resolution | ⬜ Todo |
| 007 | At-Least-Once Processing Semantics | ⬜ Todo |
| 008 | Input Validation and Sanitization | ⬜ Todo |
| 009 | PII Protection (Encryption and Masking) | ⬜ Todo |
| 010 | Transactional Outbox Pattern | ⬜ Todo |

### P1 — Important (Should Have)

| # | Task | Status |
|---|------|--------|
| 011 | Backpressure Handling for High Load | ⬜ Todo |
| 012 | Field-Level Ownership Enforcement | ⬜ Todo |
| 013 | Event Versioning Strategy | ⬜ Todo |
| 014 | Distributed Tracing and Log Correlation | ⬜ Todo |
| 015 | Periodic Consistency Validation | ⬜ Todo |
| 016 | Soft Delete and GDPR Handling | ⬜ Todo |
| 017 | CQRS Read Model | ⬜ Todo |
| 018 | Saga Pattern for Distributed Transactions | ⬜ Todo |
| 019 | Metrics and Observability | ⬜ Todo |
| 020 | Third-Party API Latency Handling | ⬜ Todo |
| 021 | Redis Cache Implementation with Invalidation | ⬜ Todo |
| 022 | Schema Evolution and Backward Compatibility | ⬜ Todo |
| 023 | Complete Audit Trail | ⬜ Todo |
| 024 | Event Replay for State Rebuilding | ⬜ Todo |
| 025 | Optimistic Locking for Concurrent Updates | ⬜ Todo |

### P2 — Nice to Have

| # | Task | Status |
|---|------|--------|
| 026 | Bulk Processing (Batch Mode) | ⬜ Todo |
| 027 | Write Optimization — Only Update Changed Fields | ⬜ Todo |
| 028 | Drift Detection Alerting | ⬜ Todo |
| 029 | Data Quality Metrics | ⬜ Todo |
| 030 | Contract Testing for Events | ⬜ Todo |


---

## 📈 Relevance to Real-Time Betting Platforms

This architecture directly addresses challenges in **sportsbook and real-time betting systems**:

| MDM Concept | Betting Platform Application |
|-------------|------------------------------|
| **Canonical ID (nationalId)** | Player ID / Account ID as single source of truth |
| **Duplicate Detection** | Prevent multi-account bonus abuse, enforce responsible gambling limits |
| **Conflict Resolution** | Consolidate player data from web, mobile, retail, affiliate partners |
| **TRUSTED_SOURCE Strategy** | Prefer KYC-verified data over self-reported information |
| **MERGE Strategy** | Aggregate multiple phone numbers, emails, payment methods |
| **Audit Trail** | Regulatory compliance, dispute resolution, gambling history |
| **SLI/SLO Monitoring** | Platform availability guarantees, latency SLAs for bet placement |
| **Event Ordering** | Guaranteed bet sequence, odds change causality |
| **Idempotency** | Prevent duplicate bet submission, safe retry on network failures |

---

## 📄 License

This project is provided for demonstration and educational purposes.

---

**Author:** Mojtaba Rahbari  
**Technologies:** Spring Boot 3.2 · Java 21 · Kafka · PostgreSQL · Kubernetes · Prometheus · OAuth2 · Docker  
**Architecture Patterns:** CQRS · Event-Driven · Idempotency · Conflict Resolution · SRE Observability
