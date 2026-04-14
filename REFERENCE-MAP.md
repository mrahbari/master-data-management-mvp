# Resume Claims → Source Code Reference Map

This file maps each resume bullet point to the exact implementation files and line numbers in this project, so you can defend every claim during technical interviews.

---

## Claim #1: Event-Driven Microservices with Dual-Key Idempotency & Partition Ordering

> *"Designed event-driven microservices architecture using Kafka, implementing dual-key idempotency (client + SHA-256 deterministic) and partition ordering strategies, eliminating duplicate processing and reducing real-time data latency."*

| Concept | File Path | Key Lines |
|---------|-----------|-----------|
| **Dual-key idempotency workflow** (client key first, fallback to SHA-256 deterministic) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/IdempotencyService.java` | L65–93 (`processKey` method) |
| **SHA-256 deterministic key generation** (`SHA-256(nationalId\|sourceSystem)`) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/util/IdempotencyKeyGenerator.java` | L18–31 (`generate` method) |
| **Client key SHA-256 hashing** | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/IdempotencyService.java` | L224–231 (`hashClientKey` method) |
| **Pessimistic locking** (prevents race conditions under concurrent requests) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/IdempotencyService.java` | L137–139 (`findByKeyHashForUpdate`) |
| **Sealed result types** (Hit / Miss / Processing / Expired) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/IdempotencyService.java` | L241–283 |
| **Kafka partition key = nationalId** (normalized) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/CustomerKafkaProducer.java` | L44–58 (`send` method — `key = partitionKey.toLowerCase().trim()`) |
| **ProducerRecord with explicit partition key** | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/CustomerKafkaProducer.java` | L49–50 (`new ProducerRecord<>(topic, null, key, event, headers)`) |
| **Topic configured with 3 partitions** | `customer-ingestion-service/src/main/java/com/mdm/ingestion/config/KafkaConfig.java` | L21 (`.partitions(3)`) |
| **Event headers for tracing** (X-Event-ID, X-Idempotency-Key, X-Source-System, X-Timestamp, X-Event-Version) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/CustomerKafkaProducer.java` | L67–75 (`buildHeaders`) |
| **Partition key JavaDoc** | `customer-ingestion-service/src/main/java/com/mdm/ingestion/dto/CustomerRawEvent.java` | L29 |
| **Kafka publish + idempotency completion** | `customer-ingestion-service/src/main/java/com/mdm/ingestion/service/IngestionUseCaseService.java` | Orchestrates the full flow |

---

## Claim #2: CQRS-Based Data Unification Pipeline with Conflict Resolution

> *"Built a CQRS-based data unification pipeline with configurable conflict resolution strategies and structured audit logging, enabling a single customer view across web, mobile, retail, and partner channels for compliance, responsible gambling, and fraud/bonus abuse prevention."*

| Concept | File Path | Key Lines |
|---------|-----------|-----------|
| **Command side** (POST `/api/customers` → Kafka → 202 Accepted) | `customer-ingestion-service/src/main/java/com/mdm/ingestion/controller/CustomerIngestionController.java` | Full file |
| **Query side** (GET `/api/customers/*` from golden records) | `customer-mastering-service/src/main/java/com/mdm/ingestion/controller/CustomerQueryController.java` | Full file |
| **Golden record dedup & merge orchestration** | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L76–86 (`processCustomerEvent`), L88–117 (`processEventInternal`) |
| **New golden record creation** | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L137–150 (`handleNewCustomer`) |
| **Existing golden record merge** | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L124–135 (`handleExistingCustomer`) |
| **Per-field conflict resolution** | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L179–214 (`mergeGoldenRecord`, `resolveField`) |
| **Phone MERGE strategy** (multi-value union) | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L195–225 (`resolvePhoneField`) |
| **Conflict resolution orchestrator** (delegates to strategy-specific resolvers) | `customer-mastering-service/src/main/java/com/mdm/mastering/conflict/ConflictResolutionService.java` | L48–61 (`resolve` method) |
| **5 strategies wired** (LATEST_UPDATE, TRUSTED_SOURCE, MOST_FREQUENT, NON_NULL, MERGE) | `customer-mastering-service/src/main/java/com/mdm/mastering/conflict/ConflictResolutionService.java` | L28–39 |
| **Configurable per-field strategies from YAML** | `customer-mastering-service/src/main/java/com/mdm/mastering/conflict/ConflictResolutionConfig.java` | L38–58 (`getConfigForField`) |
| **Structured JSON conflict audit logging** (masked nationalId, strategy, reason) | `customer-mastering-service/src/main/java/com/mdm/mastering/conflict/ConflictLogger.java` | L41–62 (`logConflict`) |
| **Conflict resolution Prometheus metrics** (`conflicts_resolved_total` by strategy tag) | `customer-mastering-service/src/main/java/com/mdm/mastering/conflict/ConflictResolutionService.java` | L67–83 (`ConflictMetrics` inner class) |
| **Raw event storage** (immutable audit trail) | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | Stored via `CustomerRawRepository` |
| **Golden record versioning** (optimistic concurrency) | `customer-mastering-service/src/main/java/com/mdm/mastering/service/GoldenRecordService.java` | L171 (`version` field, incremented on update) |
| **Idempotent Kafka consumer** (skip if event_id already processed) | `customer-mastering-service/src/main/java/com/mdm/mastering/listener/CustomerRawEventListener.java` | Full file |

---

## Claim #3: SRE-Driven Observability (Prometheus, Health Indicators, SLO Tracking)

> *"Implemented SRE-driven observability framework using Prometheus metrics, custom health indicators, and SLO tracking (availability, error budgets, burn rate), improving system reliability and early incident detection."*

| Concept | File Path | Key Lines |
|---------|-----------|-----------|
| **SLI metrics registry** (7 SLIs with Micrometer Timer/Counter) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L28–87 (all metric definitions) |
| **SLI-001: Event processing latency** (Timer, SLO: 99% < 100ms, buckets 10ms–1s) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L53–62 |
| **SLI-002: Deduplication lookup latency** (Timer, SLO: 99% < 10ms, buckets 1ms–50ms) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L64–73 |
| **SLI-003: Processing errors** (Counter, SLO: error rate < 0.1%) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L81–84 |
| **SLI-004–007: Duplicates, golden records, throughput** | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L66–80, L86–87 |
| **Error rate & duplicate rate calculation** | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/MdmSliMetrics.java` | L193–213 (`getErrorRate`, `getDuplicateRate`) |
| **Burn rate calculator** (`Burn Rate = Current Error Rate / Allowed Error Rate`) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/BurnRateCalculator.java` | L45–62 (`calculateBurnRate`) |
| **Error budget remaining** | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/BurnRateCalculator.java` | L70–84 (`calculateErrorBudgetRemaining`) |
| **Monthly error budget** (43.2 min for 99.9%) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/BurnRateCalculator.java` | L96–100 (`getMonthlyErrorBudgetMinutes`) |
| **Weekly/daily error budgets** | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/BurnRateCalculator.java` | L108–123 |
| **Burn rate severity levels** (CRITICAL ≥10x, HIGH ≥5x, ELEVATED ≥2x, NORMAL) | `customer-mastering-service/src/main/java/com/mdm/mastering/metrics/BurnRateCalculator.java` | L153–167 (`getBurnRateSeverity`) |
| **`/actuator/slo-status` custom endpoint** (availability, burn rate, budget, severity, timestamp) | `customer-mastering-service/src/main/java/com/mdm/mastering/endpoint/SloEndpoint.java` | L55–109 (`sloStatus` read operation) |
| **Health: Database** | `customer-mastering-service/src/main/java/com/mdm/mastering/health/DatabaseHealthIndicator.java` | Full file |
| **Health: Kafka** | `customer-mastering-service/src/main/java/com/mdm/mastering/health/KafkaHealthIndicator.java` | Full file |
| **Health: MDM processing** (recency check, duplicate rate anomaly detection) | `customer-mastering-service/src/main/java/com/mdm/mastering/health/MdmProcessingHealthIndicator.java` | L31–61 (`health` method) |
| **Observability config** (JVM GC, memory, thread, CPU metrics + TimedAspect) | `customer-mastering-service/src/main/java/com/mdm/mastering/config/ObservabilityConfig.java` | L24–44 |
| **SLI/SLO definitions in JavaDoc** | `customer-mastering-service/src/main/java/com/mdm/mastering/config/ObservabilityConfig.java` | L13–21 |

---

## Quick Interview Cheat Sheet

| If they ask about… | Point to… |
|---------------------|-----------|
| **How idempotency works** | `IdempotencyService.java` L65–93 — show the dual-key flow with sealed result types |
| **How you prevent duplicates** | `IdempotencyKeyGenerator.java` (SHA-256), `IdempotencyService.java` L137 (pessimistic locking), `CustomerKafkaProducer.java` L45 (partition key) |
| **Kafka partition ordering** | `CustomerKafkaProducer.java` L45 (`key.toLowerCase().trim()`), `KafkaConfig.java` L21 (3 partitions) |
| **Conflict resolution** | `ConflictResolutionService.java` L28–39 (5 strategies), `ConflictResolutionConfig.java` (YAML-driven), `ConflictLogger.java` (JSON audit) |
| **CQRS pattern** | Ingestion controller (command) vs Query controller (query), connected via Kafka |
| **SLO tracking** | `SloEndpoint.java` L55–109 (`/actuator/slo-status`), `BurnRateCalculator.java` L45–62 |
| **SLI metrics** | `MdmSliMetrics.java` L53–87 (7 SLIs with Timers, Counters, percentile histograms) |
| **Health monitoring** | 3 custom `HealthIndicator` classes in `health/` package |

---

*Generated from actual source code. All line numbers reference the current state of the repository.*
