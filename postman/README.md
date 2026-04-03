# 📮 Postman Collection Guide - MDM MVP

## 📦 Import Instructions

### 1. Import Collection

1. Open Postman
2. Click **Import** (top left)
3. Select `postman/MDM-MVP-Collection.json`
4. Click **Import**

### 2. Import Environment

1. Click **Import** again
2. Select `postman/MDM-MVP-Environment.json`
3. Click **Import**
4. Select "MDM MVP - Local" environment (top right dropdown)

---

## 🚀 Quick Start

### Option 1: Using Docker Compose (Recommended)

```bash
cd mdm-mvp
docker-compose up -d
```

This starts all services including the OAuth2 server:
- **OAuth2 Server**: http://localhost:9999
- **Ingestion Service**: http://localhost:8080
- **Mastering Service**: http://localhost:8081
- **Kafka**: localhost:9092
- **PostgreSQL**: localhost:5432

**Wait for all services to be healthy:**
```bash
docker-compose ps
# All services should show "(healthy)"
```

### Option 2: Start OAuth2 Server Manually

If you're running the services without Docker Compose:

```bash
cd mdm-mvp/oauth-server
node server.js
```

**Expected Output:**
```
========================================
  OAuth2 Authorization Server Started
========================================

📍 Server running on: http://localhost:9999

📋 Endpoints:
  Token:        POST http://localhost:9999/oauth/token
  Authorize:    GET  http://localhost:9999/oauth/authorize
  User Info:    GET  http://localhost:9999/oauth/userinfo
  JWKS:         GET  http://localhost:9999/.well-known/jwks.json
  Discovery:    GET  http://localhost:9999/.well-known/openid-configuration

👥 Test Users:
  admin / admin123 (ADMIN, CUSTOMER_WRITE, CUSTOMER_READ)
  user / user123 (CUSTOMER_WRITE)
  readonly / readonly123 (CUSTOMER_READ)

========================================
```

### Step 2: Start MDM Services

```bash
# Option 1: Docker Compose
docker-compose up --build

# Option 2: Manual (if services built)
java -jar customer-ingestion-service/build/libs/*.jar &
java -jar customer-mastering-service/build/libs/*.jar &
```

### Step 3: Get OAuth2 Token

**In Postman:**

1. Go to folder **"00 - OAuth2 Authentication"**
2. Run request **"Get Token - Admin User"**
3. Token automatically saved to `{{oauth_token}}`

**Expected Response:**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### Step 4: Test API with Token

1. Go to folder **"02 - Command Side (Write)"**
2. Run **"Ingest Customer - John Doe"**
3. Request automatically uses `{{oauth_token}}` for authorization

**Expected Response:** `202 Accepted`

> **Note:** All API requests that require authentication have the `Authorization: Bearer {{oauth_token}}` header automatically configured. Once you obtain a token in Step 3, all protected endpoints will use it automatically.

---

## 🔐 OAuth2 Token Management

### 00 - OAuth2 Authentication (6 requests) ⭐ NEW!

| Request | Method | Purpose | Auto-saves Token |
|---------|--------|---------|------------------|
| **Get Token - Admin User** | POST | Full access token | `{{oauth_token}}`, `{{admin_token}}` |
| **Get Token - Regular User** | POST | Write access only | `{{user_token}}` |
| **Get Token - Read-Only User** | POST | Read access only | `{{readonly_token}}` |
| **Get Token - Client Credentials** | POST | Machine-to-machine | `{{client_token}}` |
| **Get User Info** | GET | Get current user | - |
| **OAuth2 Discovery** | GET | OAuth2 configuration | - |

**Test Users:**
- `admin` / `admin123` → `ADMIN`, `CUSTOMER_WRITE`, `CUSTOMER_READ`
- `user` / `user123` → `CUSTOMER_WRITE`
- `readonly` / `readonly123` → `CUSTOMER_READ`

---

### 01 - Health & Setup (3 requests)

| Request | Method | Purpose |
|---------|--------|---------|
| Health Check - Ingestion | GET | Verify ingestion service |
| Health Check - Mastering | GET | Verify mastering service |
| Detailed Health | GET | See all 5 health indicators |

---

### 02 - Command Side (Write) (3 requests)

| Request | Method | Auth Required |
|---------|--------|---------------|
| Ingest Customer - John Doe | POST | ✅ Yes |
| Wait for Processing | GET | No |
| Ingest Customer - Jane Smith | POST | ✅ Yes |

---

### 03 - Query Side (Read) (6 requests)

| Request | Method | Auth Required |
|---------|--------|---------------|
| Get Customer Count | GET | No |
| Get Customer By Email | GET | No |
| Get Customer By ID | GET | No |
| List All Customers | GET | No |
| Check Customer Exists | GET | No |

---

### 04 - Survivable Matching Examples (9 requests)

Tests all 6 matching rules:
1. Email exact match (different case)
2. Nickname match (Jon vs John)
3. Typo match (Jone vs John)
4. Phonetic match (Rupert vs Robert)
5. Phone format match
6. Email typo detection
7. Gmail dots equivalence
8. Multiple nicknames
9. Check metrics

---

### 05 - Validation & Errors (4 requests)

Tests error handling:
- Missing email (400)
- Invalid email format (400)
- Empty body (400)
- Non-existent customer (404)

---

### 06 - Observability (5 requests)

Metrics endpoints:
- All metrics
- Events processed
- Duplicates detected
- Processing latency
- Golden records created

---

### 07 - SLA/SLO/SLI Testing (9 requests)

SLO monitoring:
- Get SLO Status (Complete)
- Get SLO Health (Simple)
- Get Detailed Health
- Get Burn Rate Metrics
- Get Error Budget Calculation
- Get Latency SLI
- Get Deduplication Latency SLI
- Calculate Availability SLI
- Verify SLO Target

---

## 🔐 OAuth2 Token Management

### Automatic Token Saving

When you run **"Get Token - Admin User"**, the token is automatically saved to:
- `{{oauth_token}}` - Current token (used by API requests)
- `{{admin_token}}` - Admin-specific token

### Switch Between Users

```javascript
// In Postman Tests tab or manually set environment variable
pm.environment.set("oauth_token", pm.environment.get("admin_token"));  // Full access
pm.environment.set("oauth_token", pm.environment.get("user_token"));   // Write only
pm.environment.set("oauth_token", pm.environment.get("readonly_token")); // Read only
```

### Token Expiration

Tokens expire in **1 hour**. To refresh:
1. Re-run "Get Token - Admin User"
2. Or use refresh token (if implemented)

---

## 🧪 Testing Scenarios

### Scenario 1: Full OAuth2 Flow (5 min)

```
1. 00 - OAuth2 Authentication / Get Token - Admin User
    → Token saved to {{oauth_token}}

2. 01 - Health & Setup / Health Check - Mastering Service
    → Verify service is UP

3. 02 - Command Side / Ingest Customer - John Doe
    → 202 Accepted (with OAuth2 token)

4. 03 - Query Side / Get Customer By Email
    → Customer data returned

5. 06 - Observability / Get SLO Status
    → SLO metrics displayed
```

### Scenario 2: Role-Based Access Control (3 min)

```
1. Get Token - Admin User
   → Save to {{oauth_token}}

2. Ingest Customer - John Doe
   → 202 Accepted (admin has CUSTOMER_WRITE)

3. Get Token - Read-Only User
   → Save to {{oauth_token}}

4. Ingest Customer - Jane Smith
   → 403 Forbidden (readonly user can't write)

5. Get Customer By Email
   → 200 OK (readonly user can read)
```

### Scenario 3: Survivable Matching Demo (5 min)

```
1. Get Token - Admin User

2. Ingest Customer - John Doe
   → Creates customer

3. Ingest Customer - Duplicate (JOHN.DOE@EXAMPLE.COM)
   → Detected as duplicate

4. Ingest Customer - Nickname (Jon Doe)
   → Detected via nickname matching

5. Ingest Customer - Typo (Jone Doe)
   → Detected via Levenshtein distance

6. Get Duplicates Metric
   → Shows 3 duplicates detected
```

---

## 🎯 Interview Demo Script

### 5-Minute OAuth2 Demo

```
1. Start OAuth2 Server (30 sec)
   → "Here's our OAuth2 authorization server"

2. Get Token - Admin User (30 sec)
   → "Getting JWT token with admin credentials"
   → Show token claims (sub, roles, aud, iss, exp)

3. Ingest Customer with Token (30 sec)
   → "Using token to create customer"
   → Show 202 Accepted response

4. Try with Read-Only Token (1 min)
   → "Switch to read-only user token"
   → "Try to create customer → 403 Forbidden"
   → "Read customer → 200 OK"

5. Show SLO Dashboard (1 min)
   → "Real-time SLO monitoring"
   → "Burn rate, error budget, availability"

6. Show Health Indicators (1 min)
   → "5 health indicators: db, kafka, mdmProcessing"
   → "All UP and healthy"
```

---

## 📊 Environment Variables

| Variable | Value | Purpose |
|----------|-------|---------|
| `base_url_ingestion` | `http://localhost:8080` | Ingestion service URL |
| `base_url_mastering` | `http://localhost:8081` | Mastering service URL |
| `oauth_url` | `http://localhost:9999` | OAuth2 server URL |
| `oauth_token` | (auto) | Current OAuth2 token |
| `admin_token` | (auto) | Admin user token |
| `user_token` | (auto) | Regular user token |
| `readonly_token` | (auto) | Read-only user token |
| `client_token` | (auto) | Client credentials token |

---

## 🔍 Troubleshooting

### OAuth2 Server Not Starting

```bash
# Check if port 9999 is in use
lsof -i:9999

# Kill process if needed
kill -9 <PID>

# Restart OAuth2 server
cd oauth-server
node server.js
```

### Token Not Saving

1. Check environment is selected (top right)
2. Check console for "Token saved" message
3. Manually set `{{oauth_token}}` from response

### 401 Unauthorized

1. Verify token is not expired
2. Re-run "Get Token - Admin User"
3. Check `{{oauth_token}}` is set correctly

### 403 Forbidden

- User doesn't have required role
- Try with admin token: `{{admin_token}}`

---

## ✅ Checklist

Before interview/demo:

- [ ] Import collection and environment
- [ ] Start OAuth2 server
- [ ] Start MDM services
- [ ] Run "Get Token - Admin User"
- [ ] Verify token saved to `{{oauth_token}}`
- [ ] Run health checks (all UP)
- [ ] Test create customer (202 Accepted)
- [ ] Test role-based access (403 with readonly)
- [ ] Run survivable matching examples
- [ ] Check SLO metrics

---

**Ready to demo with full OAuth2 authentication!** 🎉
