# MDM MVP - Quick Start Guide

## Prerequisites
- Java 21+
- Docker & Docker Compose
- kubectl (for Kubernetes deployment)

---

## Quick Start with Scripts (Recommended)

```bash
# Interactive menu (easiest)
./scripts/run.sh

# Or use direct commands
./scripts/dev.sh        # Development mode
./scripts/test-api.sh   # Run tests
./scripts/stop.sh       # Stop everything
```

See `scripts/README.md` for full details.

---

## Option 1: Local Testing with Docker Compose

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

### Test the API
```bash
# Ingest a customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "John.Doe@Example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-123-4567",
    "sourceSystem": "test"
  }'

# Response: 202 Accepted

# Ingest same customer (duplicate)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "JOHN.DOE@EXAMPLE.COM",
    "firstName": "Johnny",
    "lastName": "Doe",
    "phone": "+1-555-999-8888",
    "sourceSystem": "test"
  }'

# This will be detected as duplicate (same normalized email)
```

### Check metrics
```bash
# Ingestion service metrics
curl http://localhost:8080/actuator/prometheus

# Mastering service metrics
curl http://localhost:8081/actuator/prometheus

# Look for:
# - processed_events_total
# - duplicates_detected_total
# - golden_records_created_total
```

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f customer-mastering-service
```

### Stop services
```bash
docker-compose down
```

---

## Option 2: Kubernetes Deployment

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
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "sourceSystem": "k8s-test"
  }'
```

### Cleanup
```bash
kubectl delete namespace mdm-system
```

---

## Option 3: Run Services Individually (Development)

### Start dependencies
```bash
# Only Kafka and PostgreSQL
docker-compose up kafka postgres
```

### Run Ingestion Service
```bash
cd customer-ingestion-service
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Run Mastering Service
```bash
cd customer-mastering-service
./gradlew bootRun --args='--spring.profiles.active=dev'
```

---

## Troubleshooting

### Kafka connection issues
```bash
# Check Kafka is running
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Expected: customer.raw, customer.mastered
```

### Database connection issues
```bash
# Check PostgreSQL is running
docker-compose exec postgres psql -U mdm_user -d mdm_db -c "SELECT 1"

# Check tables exist
docker-compose exec postgres psql -U mdm_user -d mdm_db -c "\dt"
```

### Service not starting
```bash
# Check logs
docker-compose logs customer-ingestion-service

# Common issues:
# - Port already in use: Change port in application.yml
# - Kafka not ready: Wait for healthcheck
# - DB not initialized: Check init.sql mounted
```

### Reset everything
```bash
# Docker Compose
docker-compose down -v  # Removes volumes

# Kubernetes
kubectl delete namespace mdm-system
```

---

## Expected Behavior

### First customer submission
```
Logs:
INFO  Received customer ingestion request: eventId=abc123, email=John.Doe@Example.com
INFO  Successfully published event to Kafka: eventId=abc123, partition=1, offset=0
INFO  Received raw customer event: eventId=abc123, email=John.Doe@Example.com
INFO  Creating new golden record: email=john.doe@example.com, eventId=abc123
INFO  Successfully published mastered event: eventId=xyz789, action=CREATED
```

### Duplicate customer submission
```
Logs:
INFO  Received raw customer event: eventId=def456, email=JOHN.DOE@EXAMPLE.COM
INFO  Duplicate detected: email=john.doe@example.com, eventId=def456, goldenRecordId=...
INFO  Successfully published mastered event: eventId=uvw321, action=UPDATED
```

### Metrics after processing
```
# HELP processed_events_total Total number of customer raw events processed
# TYPE processed_events_total counter
processed_events_total{application="customer-mastering-service"} 2.0

# HELP duplicates_detected_total Total number of duplicate customer records detected
# TYPE duplicates_detected_total counter
duplicates_detected_total{application="customer-mastering-service"} 1.0

# HELP golden_records_created_total Total number of new golden records created
# TYPE golden_records_created_total counter
golden_records_created_total{application="customer-mastering-service"} 1.0

# HELP golden_records_updated_total Total number of golden records updated
# TYPE golden_records_updated_total counter
golden_records_updated_total{application="customer-mastering-service"} 1.0
```
