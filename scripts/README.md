# MDM MVP - Scripts

## Available Scripts

| Script | Purpose | Usage |
|--------|---------|-------|
| `run.sh` | Interactive menu for all run modes | `./scripts/run.sh` |
| `stop.sh` | Stop all services | `./scripts/stop.sh` |
| `dev.sh` | Development mode (hot reload friendly) | `./scripts/dev.sh` |
| `test-api.sh` | API integration tests | `./scripts/test-api.sh` |

---

## Quick Start

### Option 1: Interactive Menu (Recommended)
```bash
./scripts/run.sh
```
Select from menu:
1. Docker Compose
2. Kubernetes
3. Local Native
4. Build Only
5. Stop All

### Option 2: Direct Commands

```bash
# Start with Docker Compose
docker-compose up --build

# Run tests
./scripts/test-api.sh

# Stop everything
./scripts/stop.sh
```

---

## Script Details

### run.sh
Main entry point with interactive menu. Checks prerequisites and offers:
- Docker Compose deployment
- Kubernetes deployment
- Local Java execution
- Build only
- Full cleanup

### stop.sh
Stops all services across all platforms:
- Docker Compose containers
- Kubernetes namespace
- Local Java processes
- Port cleanup (8080, 8081)

### dev.sh
Development mode for coding:
- Starts only infrastructure (Kafka + Postgres) via Docker
- Builds and runs both services locally
- Enables debugging and hot reload
- Traps Ctrl+C for clean shutdown

**Use this when developing!**

### test-api.sh
Integration test suite:
- Health checks
- Validation tests (400 errors)
- Functional tests (202 accepted)
- Duplicate detection
- Metrics verification

**Run this before committing!**

---

## Examples

### Full local development session
```bash
# Terminal 1: Start infrastructure and services
./scripts/dev.sh

# Terminal 2: Run tests
./scripts/test-api.sh

# Terminal 2: Test manually
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "sourceSystem": "manual"}'

# Check metrics
curl localhost:8081/actuator/prometheus | grep duplicates

# When done: Ctrl+C in Terminal 1
# Or: ./scripts/stop.sh
```

### Docker Compose testing
```bash
# Start everything
./scripts/run.sh  # Choose option 1

# Wait for health checks (~60 seconds)

# Run tests
./scripts/test-api.sh

# View logs
docker-compose logs -f customer-mastering-service

# Stop
./scripts/stop.sh
```

### Kubernetes deployment
```bash
# Deploy to cluster
./scripts/run.sh  # Choose option 2

# Check status
kubectl get pods -n mdm-system

# Port forward
kubectl port-forward svc/customer-ingestion-service 8080:80 -n mdm-system &

# Test
./scripts/test-api.sh

# Cleanup
kubectl delete namespace mdm-system
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka connection |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/mdm_db` | PostgreSQL URL |
| `DATABASE_USERNAME` | `mdm_user` | DB username |
| `DATABASE_PASSWORD` | `mdm_password` | DB password |
| `LOG_LEVEL` | `INFO` | Root log level |

---

## Troubleshooting

### Port already in use
```bash
# Find and kill process
lsof -ti:8080 | xargs kill -9
lsof -ti:8081 | xargs kill -9

# Or use stop script
./scripts/stop.sh
```

### Docker Compose won't start
```bash
# Clean up
docker-compose down -v
docker system prune -f

# Restart
./scripts/run.sh  # Option 1
```

### Tests failing
```bash
# Check services are healthy
docker-compose ps

# Wait longer for startup
sleep 30

# Run tests again
./scripts/test-api.sh
```

### Kubernetes deployment stuck
```bash
# Check events
kubectl get events -n mdm-system --sort-by='.lastTimestamp'

# Check pod logs
kubectl logs -f deployment/customer-ingestion-service -n mdm-system

# Force delete stuck pods
kubectl delete pod --force --grace-period=0 <pod-name> -n mdm-system
```
