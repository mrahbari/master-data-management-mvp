# MDM MVP - Customer Master Data Management

## 1. Problem Definition

### What is MDM?
**Master Data Management (MDM)** is a technology framework that creates and maintains a single, authoritative source of truth for critical business data (customers, products, etc.) across an organization.

### Problem This MVP Solves
In fintech environments, customer data arrives from multiple channels (web, mobile, partners, banks, government registries) leading to:
- **Duplicate records** (same customer, different representations)
- **Inconsistent data** across systems
- **No single view** of the customer

This MVP solves: **Detecting duplicate customer records and creating a unified "Golden Record"** stored in PostgreSQL, using event-driven architecture with **nationalId** as the canonical unique identifier.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           KUBERNETES CLUSTER                            │
│                                                                         │
│  ┌──────────────────────┐         ┌──────────────────────────────────┐  │
│  │  Customer Ingestion  │         │    Customer Mastering Service    │  │
│  │       Service        │         │                                  │  │
│  │                      │         │  ┌────────────────────────────┐  │  │
│  │  ┌────────────────┐  │         │  │   Kafka Consumer           │  │  │
│  │  │ REST Controller│  │  kafka  │  │   (customer.raw)           │  │  │
│  │  │ POST /customers│──┼─────────┼─▶│                            │  │  │
│  │  └────────────────┘  │  topic  │  └────────────┬───────────────┘  │  │
│  │         │            │         │               │                  │  │
│  │         ▼            │         │               ▼                  │  │
│  │  ┌────────────────┐  │         │  ┌────────────────────────────┐  │  │
│  │  │ Idempotency    │  │         │  │   Golden Record Service    │  │  │
│  │  │ Service        │  │         │  │   - nationalId dedup       │  │  │
│  │  │ (dual-key)     │  │         │  │   - Create/Update Master   │  │  │
│  │  └────────────────┘  │         │  └────────────┬───────────────┘  │  │
│  │         │            │         │               │                  │  │
│  │         ▼            │         │               ▼                  │  │
│  │  ┌────────────────┐  │         │  ┌────────────────────────────┐  │  │
│  │  │ Kafka Producer │  │         │  │   PostgreSQL               │  │  │
│  │  │ key=nationalId │  │         │  │   - customer_raw           │  │  │
│  │  └────────────────┘  │         │  │   - customer_golden        │  │  │
│  │                      │         │  └────────────────────────────┘  │  │
│  └──────────────────────┘         │                                  │  │
│                                   └──────────────────────────────────┘  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                         Kafka (KRaft)                            │   │
│  │   Topics: customer.raw, customer.mastered                        │   │
│  │   Partition key: nationalId (per-customer ordering)              │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

**Components:**
- **2 Microservices**: Ingestion (command side) + Mastering (query side)
- **Kafka (KRaft mode)**: Event backbone with per-customer partition ordering
- **PostgreSQL**: Persistent storage for raw + golden records
- **Kubernetes**: Orchestration platform

---

## 3. Canonical Unique Key: nationalId

### Why nationalId?
The **nationalId** serves as the single source of truth for customer identity throughout the entire system:

| Layer | How nationalId Is Used |
|---|---|
| **REST API Input** | Required field, validated (12-13 alphanumeric chars) |
| **Input Sanitization** | Normalized: strip non-alphanumeric characters |
| **Idempotency** | Part of deterministic SHA-256 key: `SHA-256(normalizedNationalId|sourceSystem)` |
| **Kafka Partition Key** | `nationalId.toLowerCase().trim()` → same customer → same partition → ordering guaranteed |
| **Event Payload** | Stored in `CustomerRawEvent.nationalId` |
| **Golden Record Lookup** | `customer_golden.national_id` unique constraint (O(1) lookup) |
| **Database Index** | `idx_customer_golden_national_id` on golden table |

### Benefits
- **Deterministic deduplication**: Same nationalId + sourceSystem → same idempotency hash
- **Per-customer ordering**: Kafka partition key = nationalId guarantees events for the same customer are processed in order
- **Simplified matching**: No fuzzy email matching needed; nationalId is the authoritative identifier
- **Privacy**: SHA-256 hash stored in idempotency table, not raw nationalId

---

## 4. Data Flow (Step-by-Step)

```
Step 1: REST API Call (with optional X-Idempotency-Key header)
   POST /api/customers
   Body: { "nationalId": "123456789012", "name": "John Doe", "email": "...", ... }
        │
        ▼
Step 2: Customer Ingestion Service
   - Sanitize & validate inputs (IngestionInputSanitizer)
   - Resolve idempotency: dual-key strategy
     • Client-provided key (X-Idempotency-Key header) — if present
     • Deterministic key: SHA-256(nationalId|sourceSystem) — always checked
   - If HIT → return 200 OK with cached eventId
   - If MISS → continue
        │
        ▼
Step 3: Build Event & Publish to Kafka
   - Build CustomerRawEvent (nationalId, name, email, phone, sourceSystem, timestamp)
   - Kafka key = nationalId.toLowerCase().trim() (partition key)
   - Topic: customer.raw (3 partitions)
   - Headers: X-Event-ID, X-Idempotency-Key, X-Source-System, X-Timestamp, X-Event-Version
   - Mark idempotency key as COMPLETED
   - Return 202 Accepted
        │
        ▼
Step 4: Kafka Topic (customer.raw)
   - Event routed to partition based on nationalId hash
   - All events for same nationalId → same partition → strict ordering
        │
        ▼
Step 5: Customer Mastering Service (Consumer)
   - Consume event from customer.raw
   - Idempotency: skip if event_id already stored
   - Store raw event in customer_raw (audit trail)
   - Lookup by nationalId: SELECT * FROM customer_golden WHERE national_id = ?
        │
        ├─▶ NO MATCH → Create new golden record
        │       │
        │       ▼
        │   Insert into customer_golden
        │   Publish to customer.mastered (action: CREATED)
        │
        └─▶ MATCH FOUND → Update existing golden record
                │
                ▼
            Merge fields (coalesce strategy)
            Publish to customer.mastered (action: UPDATED)
```

---

## 5. Idempotency Design

### Dual-Key Strategy

```
Client Request
  │
  ├── X-Idempotency-Key header? ──YES──▶ Check by client key
  │                                            │
  │                                     Found? ──YES──▶ Check status
  │                                     │               │
  │                                     │               ├── COMPLETED → HIT (return cached)
  │                                     │               ├── PROCESSING → 409 Conflict
  │                                     │               └── EXPIRED → treat as new
  │                                     │
  │                                     NO
  │                                      │
  NO                                     ▼
  │                               Check deterministic key
  │                               SHA-256(nationalId|sourceSystem)
  │                                    │
  │                             Found? ──YES──▶ Same status check as above
  │                             NO
  │                              │
  └──────────────────────────────▼
                          Insert new record (PROCESSING)
                          Atomic: ON CONFLICT (key_hash) DO NOTHING
```

### TTL Configuration
| Key Type | Default TTL | Purpose |
|----------|-------------|---------|
| Client-provided (`X-Idempotency-Key`) | 24 hours | Client controls retry semantics |
| Auto-generated (deterministic) | 6 hours | Payload-based deduplication |

### Database Schema (Idempotency)
```sql
CREATE TABLE ingestion_idempotency_keys (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_hash                VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 hash
    client_idempotency_key  VARCHAR(255),                     -- Optional client key
    event_id                UUID NOT NULL,
    status                  VARCHAR(20) NOT NULL,             -- PROCESSING / COMPLETED
    created_at              TIMESTAMPTZ NOT NULL,
    expires_at              TIMESTAMPTZ NOT NULL
);
```

---

## 6. Data Model (PostgreSQL)

```sql
-- Raw customer events (immutable audit trail)
CREATE TABLE customer_raw (
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

CREATE INDEX idx_customer_raw_national_id ON customer_raw(national_id);
CREATE INDEX idx_customer_raw_event_id ON customer_raw(event_id);
CREATE INDEX idx_customer_raw_created ON customer_raw(created_at);

-- Golden record (single source of truth)
CREATE TABLE customer_golden (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    national_id         VARCHAR(64) NOT NULL UNIQUE,
    name                VARCHAR(255),
    email               VARCHAR(255),
    phone               VARCHAR(64),
    confidence_score    SMALLINT NOT NULL DEFAULT 100,
    version             BIGINT NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_source_system  VARCHAR(50)
);

CREATE INDEX idx_customer_golden_national_id ON customer_golden(national_id);
CREATE INDEX idx_customer_golden_updated ON customer_golden(updated_at);
```

---

## 7. Kafka Design

### Topics

| Topic | Partitions | Retention | Purpose |
|-------|------------|-----------|---------|
| `customer.raw` | 3 | 7 days | Ingestion events from all sources |
| `customer.mastered` | 3 | 30 days | Golden record changes (audit) |

### Key Strategy
```
Partition Key: nationalId.toLowerCase().trim()

Why?
- Ensures same customer always goes to same partition
- Maintains strict ordering for deduplication logic
- Enables idempotent processing per customer
- Scales horizontally by adding partitions
```

### Event Headers
| Header | Purpose |
|--------|---------|
| `X-Event-ID` | Unique event UUID |
| `X-Idempotency-Key` | SHA-256 deterministic key |
| `X-Source-System` | Originating system (CRM, BANK, WEB, MOBILE, etc.) |
| `X-Timestamp` | Ingestion timestamp |
| `X-Event-Version` | Schema version for evolution |

---

## 8. Implementation (Spring Boot 3.2 / Java 21)

### Customer Ingestion Service Structure
```
customer-ingestion-service/
├── src/main/java/com/mdm/ingestion/
│   ├── CustomerIngestionServiceApplication.java
│   ├── controller/
│   │   └── CustomerIngestionController.java       # POST /api/customers
│   ├── dto/
│   │   ├── CustomerIngestionRequest.java          # Request DTO
│   │   └── CustomerRawEvent.java                  # Kafka event (immutable)
│   ├── entity/
│   │   └── IdempotencyKey.java                    # JPA entity (factory methods)
│   ├── repository/
│   │   └── IdempotencyKeyRepository.java          # Atomic upsert
│   ├── service/
│   │   ├── IngestionUseCaseService.java           # Orchestrator
│   │   ├── IngestionInputSanitizer.java           # Input normalization + validation
│   │   ├── IngestionEventBuilder.java             # Event construction
│   │   ├── IdempotencyService.java                # Dual-key idempotency
│   │   └── CustomerKafkaProducer.java             # Kafka publishing
│   ├── exception/
│   │   ├── IngestionDomainException.java          # Base domain exception
│   │   ├── ConcurrentProcessingException.java     # HTTP 409
│   │   └── KafkaPublishException.java             # HTTP 500
│   ├── util/
│   │   ├── IdempotencyKeyGenerator.java           # SHA-256 key generation
│   │   ├── InputSanitizer.java                    # Static sanitization utilities
│   │   └── TimeConfig.java                        # Clock injection
│   ├── validator/
│   │   └── CustomerRequestValidator.java          # Field validators
│   └── config/
│       ├── GlobalExceptionHandler.java
│       ├── KafkaConfig.java
│       └── OAuth2SecurityConfig.java
└── src/main/resources/
    ├── application.yml
    └── db/migration/
        ├── V1__create_idempotency_keys_table.sql
        └── V2__add_client_idempotency_key.sql
```

### Customer Mastering Service Structure
```
customer-mastering-service/
├── src/main/java/com/mdm/mastering/
│   ├── CustomerMasteringServiceApplication.java
│   ├── controller/
│   │   └── CustomerQueryController.java           # GET /api/customers/*
│   ├── dto/
│   │   ├── CustomerRawEvent.java                  # Kafka consumer DTO
│   │   ├── CustomerMasteredEvent.java             # Produced event
│   │   └── CustomerQueryResponse.java             # Read model DTO
│   ├── entity/
│   │   ├── CustomerRawEntity.java                 # Raw event JPA entity
│   │   └── CustomerGoldenEntity.java              # Golden record JPA entity
│   ├── repository/
│   │   ├── CustomerRawRepository.java             # Raw event storage
│   │   └── CustomerGoldenRepository.java          # Golden record CRUD
│   ├── service/
│   │   ├── GoldenRecordService.java               # Core dedup + merge logic
│   │   ├── CustomerQueryService.java              # Read-side queries
│   │   ├── CustomerMasteredEventProducer.java     # Kafka producer
│   │   ├── DeduplicationService.java              # Utility (preserved)
│   │   ├── CustomerMatchingService.java           # Fuzzy matching interface (Phase 2)
│   │   └── SurvivableMatchingServiceImpl.java     # Fuzzy matching impl (Phase 2)
│   ├── listener/
│   │   └── CustomerRawEventListener.java          # Kafka consumer
│   ├── health/
│   │   ├── DatabaseHealthIndicator.java
│   │   ├── KafkaHealthIndicator.java
│   │   └── MdmProcessingHealthIndicator.java
│   ├── metrics/
│   │   ├── MdmSliMetrics.java
│   │   └── BurnRateCalculator.java
│   ├── endpoint/
│   │   └── SloEndpoint.java
│   └── config/
│       ├── KafkaConfig.java
│       ├── KafkaConsumerConfig.java
│       └── ObservabilityConfig.java
└── src/main/resources/
    └── application.yml
```

### SOLID Principles Applied

| Principle | Implementation |
|-----------|---------------|
| **Single Responsibility** | Each service/component has one reason to change: `IngestionInputSanitizer` (input), `IngestionEventBuilder` (events), `IdempotencyService` (idempotency), `CustomerKafkaProducer` (Kafka) |
| **Open/Closed** | `IdempotencyResult` sealed interface with 4 permitted types; new result types can be added without modifying existing code |
| **Liskov Substitution** | Proper `equals()`/`hashCode()` on JPA entities based on business keys |
| **Interface Segregation** | Entities have no public setters; state mutations via domain methods (`markCompleted()`, `markFailed()`) |
| **Dependency Inversion** | `Clock` injected for testability; services depend on repository interfaces |

---

## 9. Deduplication Logic

### Primary Strategy: nationalId Exact Match

```java
// GoldenRecordService.java
String nationalId = normalizeNationalId(event.getNationalId());
var existing = goldenRepository.findByNationalId(nationalId);

if (existing.isPresent()) {
    // Update existing golden record (merge)
} else {
    // Create new golden record
}
```

### Merge Strategy (Coalesce)
| Field | Strategy |
|-------|----------|
| `nationalId` | Immutable (never changes) |
| `name` | Coalesce: keep existing if new is null |
| `email` | Coalesce: keep existing if new is null |
| `phone` | Coalesce: keep existing if new is null |
| `sourceSystem` | Always update (track last source) |
| `version` | Incremented (optimistic locking) |

### Future: Survivable Matching (Phase 2)
The `SurvivableMatchingServiceImpl` is preserved for future use when advanced fuzzy matching is needed:
- Email similarity (Levenshtein distance)
- Name phonetic matching (Soundex)
- Nickname mapping (50+ variants)
- Phone number normalization
- Combined weighted scoring

---

## 10. Kubernetes Deployment

### Manifests Overview
| File | Purpose |
|------|---------|
| `k8s/namespace.yaml` | Isolated `mdm-system` namespace |
| `k8s/configmap.yaml` | Non-sensitive config (Kafka URLs) |
| `k8s/secrets.yaml` | Database credentials |
| `k8s/customer-ingestion-deployment.yaml` | 2 replicas + Service |
| `k8s/customer-mastering-deployment.yaml` | 2 replicas + Service |
| `k8s/postgres-deployment.yaml` | Stateful database |
| `k8s/kafka-deployment.yaml` | KRaft mode (no Zookeeper) |
| `k8s/db-init.sql` | Database schema initialization |

### Deploy Commands
```bash
# Create namespace and apply configs
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# Deploy infrastructure
kubectl apply -f k8s/postgres-deployment.yaml
kubectl apply -f k8s/kafka-deployment.yaml

# Initialize database
kubectl exec -n mdm-system postgres-pod -- psql -U mdm_user -d mdm_db -f /docker-entrypoint-initdb.d/db-init.sql

# Deploy services
kubectl apply -f k8s/customer-ingestion-deployment.yaml
kubectl apply -f k8s/customer-mastering-deployment.yaml
```

---

## 11. Observability

### Prometheus Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `processed_events_total` | Counter | Total raw events processed |
| `duplicates_detected_total` | Counter | Duplicate customers found |
| `golden_records_created_total` | Counter | New golden records |
| `golden_records_updated_total` | Counter | Updated golden records |
| `failed_events_total` | Counter | Processing failures |

### Health Indicators
- **Database**: Connection pool status
- **Kafka**: Broker connectivity
- **MDM Processing**: Processing error rate

---

## 12. Failure Handling

### Idempotent Consumer Pattern
```java
@KafkaListener(topics = "${kafka.topics.customer-raw}")
public void listen(CustomerRawEvent event, Acknowledgment ack) {
    // Skip if already processed
    if (rawRepository.existsByEventId(event.getEventId())) {
        ack.acknowledge();
        return;
    }
    // Process...
    ack.acknowledge();
}
```

### Idempotency Key Lifecycle
```
NEW → PROCESSING → COMPLETED (success)
                → COMPLETED (failure — prevents reprocessing same payload)
                → EXPIRED (after TTL — allows retry)
```

---

## 13. API Reference

### Ingestion (Command Side)

```bash
# POST /api/customers
# Auth: CUSTOMER_WRITE or ADMIN role
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "X-Idempotency-Key: optional-client-key" \
  -d '{
    "nationalId": "123456789012",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-123-4567",
    "sourceSystem": "web-portal"
  }'

# Response 202 Accepted (new):
#   { "eventId": "uuid", "status": "ACCEPTED" }
#   Header: X-Event-ID: uuid

# Response 200 OK (cached):
#   { "eventId": "uuid", "status": "CACHED" }
#   Header: X-Idempotency-Replay: true

# Response 409 Conflict (still processing)
# Response 400 Bad Request (validation error)
```

### Query (Read Side)

```bash
# GET /api/customers — List with pagination
# GET /api/customers/{id} — By ID
# GET /api/customers/by-national-id?nationalId=123456789012 — By national ID
# GET /api/customers/search?name=John — Search by name
# GET /api/customers/exists?nationalId=123456789012 — Existence check
# GET /api/customers/count — Total count
```

---

## 14. Summary

### Architecture Overview
> "I designed a two-service MDM system using event-driven architecture.
> The **Customer Ingestion Service** receives REST requests, resolves idempotency via a dual-key strategy (client-provided + SHA-256 deterministic), and publishes events to Kafka partitioned by **nationalId**.
> The **Customer Mastering Service** consumes these events, deduplicates by **nationalId**, and maintains a golden record in PostgreSQL.
> All changes are published back to Kafka for downstream systems."

### Key Design Decisions
> "**Why nationalId?** It's the most reliable unique identifier for customers in a fintech context. Email-based deduplication is error-prone (typos, aliases, shared emails). NationalId provides deterministic, unambiguous identification.
>
> **Why Kafka?** It provides durability, replayability, and loose coupling. If the mastering service goes down, events are preserved. Partitioning by nationalId ensures per-customer ordering.
>
> **Why async processing?** The REST API returns 202 Accepted immediately, allowing the system to handle traffic spikes without blocking users.
>
> **Why dual-key idempotency?** Clients sometimes send `X-Idempotency-Key` and sometimes don't. The deterministic key (`SHA-256(nationalId|sourceSystem)`) ensures the same payload is never duplicated regardless."

### Trade-offs
> "**What I simplified:** Single-field deduplication (nationalId exact match). This covers the primary use case and can be extended with fuzzy matching later.
>
> **What I didn't include:**
> 1. No Saga pattern, no complex orchestration. The system is eventually consistent, which is acceptable for customer data.
> 2. No ML-enhanced matching yet — rule-based exact match for now.
>
> **Scalability:** Services are stateless and can scale horizontally. Kafka partitions can increase if needed."

---

## 15. 🗺️ Roadmap: From MVP to Production

See **[ROADMAP.md](docs/ROADMAP.md)** for the complete implementation plan.

### Current State (MVP) ✅

| Category | Feature | Status |
|----------|---------|--------|
| **Architecture** | CQRS pattern (Command/Query separation) | ✅ Complete |
| **Architecture** | Event-driven with Kafka | ✅ Complete |
| **Data Flow** | Async processing with 202 Accepted | ✅ Complete |
| **Identity** | nationalId as canonical unique key | ✅ Complete |
| **Deduplication** | nationalId exact match | ✅ Complete |
| **Idempotency** | Dual-key strategy (client + deterministic SHA-256) | ✅ Complete |
| **Ordering** | Kafka partition key = nationalId | ✅ Complete |
| **Storage** | PostgreSQL with raw + golden tables | ✅ Complete |
| **Observability** | Health indicators (DB, Kafka, Processing) | ✅ Complete |
| **Observability** | Prometheus metrics + SLI/SLO tracking | ✅ Complete |
| **Security** | OAuth2 Resource Server with JWT | ✅ Complete |
| **Code Quality** | SOLID principles, immutable entities, domain methods | ✅ Complete |
| **Deployment** | Docker Compose + Kubernetes manifests | ✅ Complete |
| **CI/CD** | GitHub Actions workflow | ✅ Complete |

### Implementation Phases

#### Phase 1: Reliability & Resilience (Next 2-4 Weeks)
- **DLQ Error Handling** — Dead Letter Queue for poison messages
- **Outbox Pattern** — Reliable event publishing with atomic commits

#### Phase 2: Advanced Matching (Next 4-8 Weeks)
- **Survivable Matching** — Enable fuzzy matching (email similarity, phonetic names, nicknames)
- **Human-in-the-Loop** — Manual review queue for low-confidence matches
- **Confidence Scoring** — Probabilistic match scoring

#### Phase 3: Scalability & Performance (Next 8-12 Weeks)
- **Consumer Group Scaling** — Kubernetes HPA based on Kafka lag
- **Redis Caching** — Cache hot records for faster deduplication
- **Batch Processing** — Optimized bulk operations

#### Phase 4: Enterprise Features (Next 12-16 Weeks)
- **Data Quality Scoring** — Completeness, accuracy, consistency metrics
- **Audit Trail** — Full compliance audit logging
- **GDPR Compliance** — Right to erasure, data portability

---

## Appendix: Quick Reference

### Build Commands
```bash
# Compile both services
./gradlew :customer-ingestion-service:compileJava :customer-mastering-service:compileJava

# Build all (includes code quality checks)
./gradlew fullBuild

# Run all tests
./gradlew fullTest

# Code quality checks
./gradlew fullCodeQuality

# Format code
./gradlew :customer-ingestion-service:formatCode :customer-mastering-service:formatCode
```

### Local Testing (Docker Compose)
```bash
docker-compose up -d kafka postgres
# Then run services locally or build Docker images
```

### Metrics Endpoint
```bash
curl http://localhost:8080/actuator/prometheus
```
