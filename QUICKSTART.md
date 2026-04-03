# MDM MVP - Quick Start Guide

## Prerequisites
- **Docker & Docker Compose** (required for running services)
- **Java 21+** (optional, only needed for local development)
- **kubectl** (optional, only needed for Kubernetes deployment)
- **curl** (for testing APIs)

---

## Quick Start with Docker (Easiest)

### Start All Services

```bash
# Navigate to project root
cd master-data-management-mvp

# Start all services (OAuth server, Kafka, PostgreSQL, both microservices)
docker-compose down
docker-compose up --build

#💣 If the problem persists (complete cleaning)
docker-compose down -v
docker network prune -f
docker-compose up --build
```

**What this starts:**
- **OAuth Server** (port 9999) - Mock JWT authentication
- **Kafka** (port 9092) - Event streaming
- **PostgreSQL** (port 5432) - Database storage
- **Customer Ingestion Service** (port 8080) - REST API for ingesting customers
- **Customer Mastering Service** (port 8081) - Golden record management & queries

### Wait for Services to Be Healthy

```bash
# Check service health (all should show "healthy")
docker-compose ps
```

Services typically take **30-60 seconds** to become healthy. Wait until all containers show `(healthy)` status.

### Test the API

```bash
# Check health endpoints
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health

# Ingest a new customer (requires nationalId)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "nationalId": "123456789012",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-123-4567",
    "sourceSystem": "test"
  }'

# Response: 202 Accepted with {"eventId": "uuid", "status": "ACCEPTED"}
```

### Query Golden Records

```bash
# Wait a few seconds for async processing, then query:

# Get customer count
curl http://localhost:8081/api/customers/count

# List all customers
curl "http://localhost:8081/api/customers?page=0&size=20"

# Get by national ID
curl "http://localhost:8081/api/customers/by-national-id?nationalId=123456789012"
```

### Stop Services

```bash
# Stop all containers
docker-compose down

# Stop and remove volumes (fresh start)
docker-compose down -v
```

---

## Running with Helper Scripts (Recommended)

The project includes interactive scripts for easier management:

```bash
# Interactive menu (easiest way to run)
./scripts/run.sh

# Development mode (infrastructure via Docker, services run locally)
./scripts/dev.sh

# Test the API with automated test suite
./scripts/test-api.sh

# Stop all services
./scripts/stop.sh
```

### What Each Script Does

| Script | Purpose |
|--------|---------|
| `scripts/run.sh` | Interactive menu: Docker Compose, Kubernetes, local Java, build-only, or stop |
| `scripts/dev.sh` | Dev mode: Starts Kafka+Postgres in Docker, runs both services locally with hot-reload |
| `scripts/test-api.sh` | Automated API tests: Validates health, ingestion, dedup, and metrics |
| `scripts/stop.sh` | Cleanup: Stops Docker containers, Kubernetes, and local Java processes |

---

## Option 1: Docker Compose (Full Stack)

### Start all services
```bash
docker-compose up --build
```

### Verify services are running
```bash
# Check container health
docker-compose ps

# Expected output: All services should be "healthy"
```

### Test duplicate detection

```bash
# 1. Submit first customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "nationalId": "123456789012",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-123-4567",
    "sourceSystem": "web-portal"
  }'

# Response: 202 Accepted (new golden record created)

# 2. Submit duplicate customer (same nationalId)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "nationalId": "123456789012",
    "name": "Johnny Doe",
    "email": "johnny.doe@example.com",
    "phone": "+1-555-999-8888",
    "sourceSystem": "mobile-app"
  }'

# Response: 202 Accepted (detected as duplicate, golden record updated)

# 3. Query the golden record
curl "http://localhost:8081/api/customers/by-national-id?nationalId=123456789012"
```

### Check metrics
```bash
# Ingestion service metrics
curl http://localhost:8080/actuator/prometheus

# Mastering service metrics
curl http://localhost:8081/actuator/prometheus

# Look for:
# - http_server_requests_seconds_count
# - mdm.events_processed_total
# - mdm.duplicates_detected_total
# - mdm.golden_records_created_total
```

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f customer-mastering-service

# Last 100 lines
docker-compose logs --tail=100 customer-ingestion-service
```

### Stop services
```bash
docker-compose down
```

---

## Option 2: Run Infrastructure in Docker, Services Locally (Development)

This is useful for development when you want to debug the Java services locally.

### Start dependencies
```bash
# Only Kafka and PostgreSQL
docker-compose up -d kafka postgres oauth-server

# Wait for services
docker-compose ps
```

### Run Ingestion Service
```bash
cd customer-ingestion-service

# Option A: Using Gradle (auto-reload)
./gradlew bootRun

# Option B: Using JAR
./gradlew clean build -x test
java -jar build/libs/customer-ingestion-service-1.0.0.jar
```

### Run Mastering Service
```bash
cd customer-mastering-service

# Option A: Using Gradle (auto-reload)
./gradlew bootRun

# Option B: Using JAR
./gradlew clean build -x test
java -jar build/libs/customer-mastering-service-1.0.0.jar
```

### Or use the dev script
```bash
# Automates the entire setup
./scripts/dev.sh
```

---

## Option 3: Kubernetes Deployment

### Apply manifests
```bash
# Create namespace
kubectl apply -f k8s/namespace.yaml

# Apply configs
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# Deploy infrastructure
kubectl apply -f k8s/kafka-deployment.yaml
kubectl apply -f k8s/postgres-deployment.yaml

# Wait for infrastructure
kubectl wait --for=condition=ready pod -l app=kafka -n mdm-system --timeout=120s
kubectl wait --for=condition=ready pod -l app=postgres -n mdm-system --timeout=60s

# Deploy services
kubectl apply -f k8s/customer-ingestion-deployment.yaml
kubectl apply -f k8s/customer-mastering-deployment.yaml
```

### Verify deployment
```bash
# Check pods
kubectl get pods -n mdm-system

# Check services
kubectl get services -n mdm-system

# Port forward for testing
kubectl port-forward svc/customer-ingestion-service 8080:80 -n mdm-system &
kubectl port-forward svc/customer-mastering-service 8081:80 -n mdm-system &
```

### Test the API
```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "nationalId": "123456789012",
    "name": "Test User",
    "email": "test@example.com",
    "sourceSystem": "k8s-test"
  }'
```

### Cleanup
```bash
kubectl delete namespace mdm-system
```

---

## Troubleshooting

### Docker Network Error (Enable IPv4/IPv6 Changed)

If you see this error:
```
ERROR: Network "master-data-management-mvp_default" needs to be recreated - option "com.docker.network.enable_ipv6" has changed
```

**Fix:**
```bash
# Stop everything and remove the broken network
docker-compose down --remove-orphans

# Start fresh (network will be recreated automatically)
docker-compose up --build -d
```

> **Why this happens:** Docker Compose auto-generates a network with internal defaults.
> When Docker updates or system settings change (e.g., IPv6 toggle), those defaults
> shift but the existing network keeps the old config. The `docker-compose.yml` now
> explicitly declares `com.docker.network.enable_ipv6: "false"` to prevent this,
> but if you hit it once, run the commands above to recreate it.

### Services Won't Start

**Check Docker is running:**
```bash
docker ps
docker-compose version
```

**Check ports are available:**
```bash
# Linux
lsof -i:8080
lsof -i:8081
lsof -i:5432
lsof -i:9092
lsof -i:9999

# Kill processes using ports
lsof -ti:8080 | xargs kill -9
```

### Kafka connection issues
```bash
# Check Kafka is running
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Expected topics: customer.raw, customer.mastered, __consumer_offsets
```

### Database connection issues
```bash
# Check PostgreSQL is running
docker-compose exec postgres psql -U mdm_user -d mdm_db -c "SELECT 1"

# Check tables exist
docker-compose exec postgres psql -U mdm_user -d mdm_db -c "\dt"

# Expected tables: customer_raw, customer_golden, ingestion_idempotency_keys
```

### Service not starting
```bash
# Check logs
docker-compose logs customer-ingestion-service
docker-compose logs customer-mastering-service

# Common issues:
# - Port already in use: Kill process or change port in docker-compose.yml
# - Kafka not ready: Wait for healthcheck to pass
# - DB not initialized: Check init.sql is mounted correctly
# - OAuth server not ready: Services depend on oauth-server health
```

### Kafka Deserialization Errors

**Symptom:** Errors like `Can't deserialize data from topic [customer.raw]`

**Cause:** Non-JSON messages written to Kafka topic (e.g., from health checks)

**Solution:**
```bash
# Stop all services and remove Kafka volumes
docker-compose down -v

# Restart with fresh Kafka
docker-compose up --build
```

### Reset Everything
```bash
# Docker Compose with volume cleanup
docker-compose down -v  # Removes all data

# Kubernetes
kubectl delete namespace mdm-system

# Kill local Java processes
lsof -ti:8080 | xargs kill -9
lsof -ti:8081 | xargs kill -9
```

---

## Expected Behavior

### First customer submission
```
Ingestion Service Logs:
INFO  Received customer ingestion request: nationalId=123456789012, sourceSystem=test
INFO  Idempotency check: cache miss, processing
INFO  Successfully published event to Kafka: eventId=abc123, partition=1, offset=0

Mastering Service Logs:
INFO  Received raw customer event: eventId=abc123, nationalId=123456789012
INFO  Creating new golden record: nationalId=123456789012, eventId=abc123
INFO  Successfully published mastered event: eventId=xyz789, action=CREATED
```

### Duplicate customer submission
```
Mastering Service Logs:
INFO  Received raw customer event: eventId=def456, nationalId=123456789012
INFO  Duplicate detected: nationalId=123456789012, eventId=def456, goldenRecordId=...
INFO  Updated golden record: nationalId=123456789012
INFO  Successfully published mastered event: eventId=uvw321, action=UPDATED
```

### Metrics after processing
```
# HELP mdm_events_processed_total Total number of customer raw events processed
# TYPE mdm_events_processed_total counter
mdm_events_processed_total{application="customer-mastering-service"} 2.0

# HELP mdm_duplicates_detected_total Total number of duplicate customer records detected
# TYPE mdm_duplicates_detected_total counter
mdm_duplicates_detected_total{application="customer-mastering-service"} 1.0

# HELP mdm_golden_records_created_total Total number of new golden records created
# TYPE mdm_golden_records_created_total counter
mdm_golden_records_created_total{application="customer-mastering-service"} 1.0

# HELP mdm_golden_records_updated_total Total number of golden records updated
# TYPE mdm_golden_records_updated_total counter
mdm_golden_records_updated_total{application="customer-mastering-service"} 1.0
```

---

## API Reference Quick Guide

### Ingestion Service (Port 8080)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/customers` | OAuth2 | Ingest new customer |
| `GET /actuator/health` | Public | Health check |
| `GET /actuator/prometheus` | Public | Prometheus metrics |

### Mastering Service (Port 8081)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/customers` | Public | List all customers (paginated) |
| `GET /api/customers/{id}` | Public | Get customer by ID |
| `GET /api/customers/by-national-id?nationalId=...` | Public | Get by national ID |
| `GET /api/customers/search?name=...` | Public | Search by name |
| `GET /api/customers/exists?nationalId=...` | Public | Check if customer exists |
| `GET /api/customers/count` | Public | Total customer count |
| `GET /actuator/health` | Public | Health check |
| `GET /actuator/prometheus` | Public | Prometheus metrics |

---

## Architecture Components

| Component | Port | Description |
|-----------|------|-------------|
| OAuth Server | 9999 | Mock JWT authentication server |
| Kafka | 9092 | Event streaming (KRaft mode, no Zookeeper) |
| PostgreSQL | 5432 | Database for golden records |
| Ingestion Service | 8080 | REST API + Kafka producer |
| Mastering Service | 8081 | Kafka consumer + golden record management |

### Data Flow
```
POST /api/customers → Ingestion Service → Kafka (customer.raw) → Mastering Service → PostgreSQL (Golden Record)
```

---

**Need more details?** See [README.md](README.md) for architecture, [HOW-TO-RUN.md](HOW-TO-RUN.md) for comprehensive guide.
