# MDM MVP - How to Run the Project

## ✅ Build Status

**Both services built successfully!**

```
✅ customer-ingestion-service-1.0.0.jar (42 MB)
✅ customer-mastering-service-1.0.0.jar (65 MB)
```

---

## 🚀 Quick Start

### Option 1: Docker Compose (Recommended)

**Prerequisites:**
- Docker installed
- Docker Compose installed

**Start all services:**
```bash
cd mdm-mvp
docker-compose up --build
```

**Services will start:**
- Kafka (port 9092)
- PostgreSQL (port 5432)
- Customer Ingestion Service (port 8080)
- Customer Mastering Service (port 8081)

**Access:**
- Ingestion API: http://localhost:8080
- Mastering API: http://localhost:8081
- Health: http://localhost:8081/actuator/health

**Stop:**
```bash
docker-compose down
```

---

### Option 2: Manual Run (Java + Docker for Infrastructure)

**Step 1: Start Infrastructure (Kafka + PostgreSQL)**

```bash
# Only Kafka and PostgreSQL
docker-compose up -d kafka postgres

# Wait for services to be ready
docker-compose ps
```

**Step 2: Run Ingestion Service**

```bash
cd customer-ingestion-service

# Run with environment variables
java -jar build/libs/customer-ingestion-service-1.0.0.jar \
  --server.port=8080 \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --kafka.topics.customer-raw=customer.raw
```

**Step 3: Run Mastering Service** (new terminal)

```bash
cd customer-mastering-service

java -jar build/libs/customer-mastering-service-1.0.0.jar \
  --server.port=8081 \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/mdm_db \
  --spring.datasource.username=mdm_user \
  --spring.datasource.password=mdm_password \
  --kafka.topics.customer-raw=customer.raw \
  --kafka.topics.customer-mastered=customer.mastered
```

---

### Option 3: Development Mode

**Start infrastructure:**
```bash
docker-compose up -d kafka postgres
```

**Run services with Gradle:**

Terminal 1 - Ingestion Service:
```bash
cd customer-ingestion-service
./gradlew bootRun
```

Terminal 2 - Mastering Service:
```bash
cd customer-mastering-service
./gradlew bootRun
```

---

## 🧪 Testing the System

### 1. Health Check

```bash
# Check if services are running
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health

# Detailed health
curl http://localhost:8081/actuator/health?show-details=always
```

### 2. Ingest Customer (Command Side)

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+1-555-123-4567",
    "sourceSystem": "test"
  }'

# Response: 202 Accepted
```

### 3. Query Customer (Query Side)

```bash
# Wait a few seconds for processing, then:

# Get by email
curl "http://localhost:8081/api/customers/by-email?email=john.doe@example.com"

# Get count
curl http://localhost:8081/api/customers/count

# List all
curl "http://localhost:8081/api/customers?page=0&size=20"
```

### 4. Test Duplicate Detection

```bash
# Submit duplicate (same email, different case)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "JOHN.DOE@EXAMPLE.COM",
    "firstName": "Johnny",
    "lastName": "Doe",
    "phone": "+1-555-999-8888",
    "sourceSystem": "test"
  }'

# Query again - should show updated record
curl "http://localhost:8081/api/customers/by-email?email=john.doe@example.com"
```

### 5. Test Survivable Matching

```bash
# Submit with nickname (Jon instead of John)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jon.doe@example.com",
    "firstName": "Jon",
    "lastName": "Doe",
    "phone": "555-123-4567",
    "sourceSystem": "test"
  }'

# Submit with typo (Jone instead of John)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "jone.doe@example.com",
    "firstName": "Jone",
    "lastName": "Doe",
    "phone": "555-123-4567",
    "sourceSystem": "test"
  }'
```

### 6. Check Metrics

```bash
# Get all metrics
curl http://localhost:8081/actuator/metrics

# Get specific metrics
curl http://localhost:8081/actuator/metrics/mdm.events_processed_total
curl http://localhost:8081/actuator/metrics/mdm.duplicates_detected_total
curl http://localhost:8081/actuator/metrics/mdm.event_processing_latency_seconds
```

---

## 📊 Import Postman Collection

1. Open Postman
2. Import `postman/MDM-MVP-Collection.json`
3. Import `postman/MDM-MVP-Environment.json`
4. Select "MDM MVP - Local" environment
5. Run requests in order

---

## 🔍 Troubleshooting

### Kafka Deserialization Errors (Poison Messages)

**Symptom:** You see errors like:
```
Can't deserialize data from topic [customer.raw]
Unrecognized token 'health': was expecting (JSON String, Number, Array, Object)
```

**Cause:** Non-JSON messages (e.g., from health checks) were written to the Kafka topic.

**Solution:** Clear Kafka data and restart:
```bash
# Stop all services and remove Kafka volumes
docker-compose down -v

# Restart with fresh Kafka
docker-compose up -d

# Wait for services to be healthy
docker-compose ps
```

**Prevention:** The health check topic is now properly configured to avoid this issue.

### Services Won't Start

**Check Java version:**
```bash
java -version
# Should be Java 21+
```

**Check ports are free:**
```bash
lsof -i:8080
lsof -i:8081
lsof -i:5432
lsof -i:9092

# Kill processes if needed
kill -9 <PID>
```

### Kafka Connection Issues

**Check Kafka is running:**
```bash
docker-compose ps kafka
docker-compose logs kafka
```

**Test Kafka connection:**
```bash
docker-compose exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Database Connection Issues

**Check PostgreSQL is running:**
```bash
docker-compose ps postgres
docker-compose logs postgres
```

**Test database connection:**
```bash
docker-compose exec postgres psql -U mdm_user -d mdm_db -c "SELECT 1"
```

---

## 📁 Project Structure

```
mdm-mvp/
├── customer-ingestion-service/
│   ├── build/libs/customer-ingestion-service-1.0.0.jar
│   └── src/main/java/com/mdm/ingestion/
├── customer-mastering-service/
│   ├── build/libs/customer-mastering-service-1.0.0.jar
│   └── src/main/java/com/mdm/mastering/
├── docker-compose.yml
├── postman/
│   ├── MDM-MVP-Collection.json
│   └── MDM-MVP-Environment.json
└── scripts/
    ├── run.sh
    ├── stop.sh
    ├── dev.sh
    └── test-api.sh
```

---

## 🎯 Key Endpoints

### Ingestion Service (Port 8080)

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| POST | `/api/customers` | OAuth2 | Ingest customer |
| GET | `/actuator/health` | Public | Health check |
| GET | `/actuator/prometheus` | Public | Metrics |

### Mastering Service (Port 8081)

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/customers` | Public | List customers |
| GET | `/api/customers/{id}` | Public | Get by ID |
| GET | `/api/customers/by-email` | Public | Get by email |
| GET | `/api/customers/search` | Public | Search by name |
| GET | `/api/customers/exists` | Public | Check existence |
| GET | `/api/customers/count` | Public | Get count |
| GET | `/actuator/health` | Public | Health check |
| GET | `/actuator/prometheus` | Public | Metrics |

---

## 🎓 Demo Script for Interviews

```bash
# 1. Start services
docker-compose up -d

# 2. Check health
curl http://localhost:8081/actuator/health

# 3. Ingest first customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email": "john.doe@example.com", "firstName": "John", "sourceSystem": "demo"}'

# 4. Wait for processing
sleep 3

# 5. Query customer
curl "http://localhost:8081/api/customers/by-email?email=john.doe@example.com"

# 6. Get count
curl http://localhost:8081/api/customers/count

# 7. Ingest duplicate (same email, different case)
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email": "JOHN.DOE@EXAMPLE.COM", "firstName": "Johnny", "sourceSystem": "demo"}'

# 8. Query again - show updated record
curl "http://localhost:8081/api/customers/by-email?email=john.doe@example.com"

# 9. Check metrics
curl http://localhost:8081/actuator/metrics/mdm.duplicates_detected_total

# 10. Show health details
curl http://localhost:8081/actuator/health?show-details=always
```

---

## ✅ Verification Checklist

- [x] JARs built successfully
- [ ] Infrastructure running (Kafka + PostgreSQL)
- [ ] Ingestion service started (port 8080)
- [ ] Mastering service started (port 8081)
- [ ] Health endpoints responding
- [ ] Can ingest customers
- [ ] Can query customers
- [ ] Duplicate detection working
- [ ] Metrics available

---

**Ready to run! Choose your preferred option above.** 🚀
