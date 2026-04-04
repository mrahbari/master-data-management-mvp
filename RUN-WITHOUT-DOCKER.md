# MDM MVP - Running Without Docker

## ⚠️ Docker Not Available

Docker daemon is not running in this environment. Here's how to run the services directly.

---

## 🚀 Option 1: Run Services Directly (No OAuth2)

### Start Ingestion Service
```bash
cd mdm-mvp/customer-ingestion-service
java -jar build/libs/customer-ingestion-service-1.0.0.jar \
  --server.port=8080 \
  --spring.security.oauth2.resourceserver.jwt.enabled=false
```

### Start Mastering Service (requires Kafka + PostgreSQL)
```bash
# You need Kafka and PostgreSQL running first
# Then:
cd mdm-mvp/customer-mastering-service
java -jar build/libs/customer-mastering-service-1.0.0.jar \
  --server.port=8081 \
  --spring.kafka.bootstrap-servers=localhost:9092 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/mdm_db
```

---

## 🧪 Option 2: Test with Mock OAuth2

### Create Test JWT Token

For testing purposes, you can use this mock JWT token:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJyb2xlcyI6WyJDVVNUT01FUl9XUklURSIsIkFETUlOIl0sImF1ZCI6Im1kbS1hcGkiLCJpc3MiOiJodHRwczovL21kbS1kZW1vLmF1dGgwLmNvbS8iLCJleHAiOjk5OTk5OTk5OTl9.test_signature
```

### Use in Postman

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

---

## 🔐 Option 3: Disable OAuth2 for Local Testing

### Update application.yml

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          enabled: false  # Disable for local testing
```

### Or use command-line flag

```bash
java -jar *.jar --spring.security.oauth2.resourceserver.jwt.enabled=false
```

---

## 📝 Quick Test (Without OAuth2)

```bash
# Start service (port 8080)
java -jar customer-ingestion-service/build/libs/customer-ingestion-service-1.0.0.jar \
  --server.port=8080 \
  --spring.security.oauth2.resourceserver.jwt.enabled=false

# Test endpoint
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "firstName": "Test",
    "lastName": "User",
    "sourceSystem": "direct-test"
  }'

# Expected: 202 Accepted
```

---

## 🎯 For Production Demo

### Start Full Stack with Docker

```bash
# On a system with Docker:
docker compose up --build

# Services will start:
# - Kafka: localhost:9092
# - PostgreSQL: localhost:5432
# - Ingestion Service: localhost:8080
# - Mastering Service: localhost:8081
```

### Get Real JWT Token

1. **Configure OAuth2 Provider** (Auth0, Cognito, etc.)
2. **Get Token:**
   ```bash
   curl -X POST https://your-auth-server.com/oauth/token \
     -H "Content-Type: application/json" \
     -d '{
       "client_id": "your-client-id",
       "client_secret": "your-client-secret",
       "audience": "mdm-api",
       "grant_type": "client_credentials"
     }'
   ```

3. **Use Token:**
   ```bash
   TOKEN="eyJhbGciOiJSUzI1NiIs..."
   
   curl -X POST http://localhost:8080/api/customers \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"email": "test@example.com", "sourceSystem": "test"}'
   ```

---

## ✅ Verification Commands

### Check Service is Running

```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected: {"status":"UP"}
```

### Test Without Auth

```bash
# Should work with OAuth2 disabled
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "sourceSystem": "test"}'

# Expected: 202 Accepted
```

### Test With Auth (OAuth2 enabled)

```bash
# Should fail without token
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "sourceSystem": "test"}'

# Expected: 401 Unauthorized

# Should work with valid token
curl -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "sourceSystem": "test"}'

# Expected: 202 Accepted
```

---

**Services are built and ready to run!** 🎉
