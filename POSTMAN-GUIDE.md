# Postman Setup Guide

## Quick Start (2 Steps)

### 1️⃣ Get Fresh Tokens

Run the token generation script:

```bash
./get-tokens.sh
```

This will:
- ✅ Verify OAuth2 server is running
- ✅ Get tokens for all users (admin, user, readonly)
- ✅ Save tokens to `jwt-tokens.json`
- ✅ Display usage instructions

### 2️⃣ Import Collection in Postman

1. Open Postman
2. Click **Import** (top left)
3. Select `postman-collection.json`
4. The collection will appear in your sidebar

### 3️⃣ Set the Token

**Option A: Collection Variable (Recommended)**

1. Click on the collection "MDM MVP API"
2. Go to **Variables** tab
3. Set `access_token` to your token from `jwt-tokens.json`
4. All requests will use `{{access_token}}`

**Option B: Per-Request Authorization**

1. Open any request
2. Go to **Authorization** tab
3. Type: **Bearer Token**
4. Token: Paste your `access_token`

---

## Test Users

| Username   | Password     | Roles                                    | Use Case                    |
|------------|--------------|------------------------------------------|-----------------------------|
| `admin`    | `admin123`   | ADMIN, CUSTOMER_WRITE, CUSTOMER_READ     | Full access to all endpoints|
| `user`     | `user123`    | CUSTOMER_WRITE                           | Create/update customers     |
| `readonly` | `readonly123`| CUSTOMER_READ                            | View customers only         |

---

## Quick Test

After importing and setting the token, run:

```bash
./test-api.sh
```

This will verify:
- ✅ OAuth2 server is running
- ✅ API service is running
- ✅ Token generation works
- ✅ Authenticated API calls work
- ✅ Reports any issues

---

## Troubleshooting 401 Errors

### Issue: Getting 401 Unauthorized

**Check 1: Token Issuer**

Decode your token and verify the issuer:

```bash
echo "YOUR_TOKEN" | cut -d'.' -f2 | base64 -d | python3 -m json.tool
```

Look for the `iss` claim. It should be:
```
"iss": "http://host.docker.internal:9999"
```

**Check 2: Services Running**

```bash
docker ps | grep -E "oauth-server|customer-ingestion"
```

Both containers should be running.

**Check 3: Correct Port**

- OAuth2 Server: `http://localhost:9999`
- API Service: `http://localhost:8080`

**Check 4: Token Expiration**

Tokens expire after 1 hour. Get a fresh token:

```bash
./get-tokens.sh
```

**Check 5: Authorization Header**

In Postman:
1. Go to request **Headers** tab
2. Verify you have: `Authorization: Bearer YOUR_TOKEN`
3. No extra spaces or quotes

---

## Manual curl Test

If you want to test without Postman:

```bash
# Get token
TOKEN=$(curl -s -X POST http://localhost:9999/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"password","client_id":"mdm-client","client_secret":"mdm-secret","username":"admin","password":"admin123"}' \
  | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

# Test API
curl -X POST http://localhost:8080/api/customers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "sourceSystem": "curl-test"}'
```

---

## Collection Endpoints

| Request Name              | Method | Endpoint                          | Auth Required | Roles Needed              |
|---------------------------|--------|-----------------------------------|---------------|---------------------------|
| Health Check              | GET    | /actuator/health                  | ❌ No         | None                      |
| Create Customer (Admin)   | POST   | /api/customers                    | ✅ Yes        | CUSTOMER_WRITE or ADMIN   |
| Get All Customers         | GET    | /api/customers                    | ✅ Yes        | CUSTOMER_READ or ADMIN    |
| Get Customer by ID        | GET    | /api/customers/:id                | ✅ Yes        | CUSTOMER_READ or ADMIN    |
| Get Token - Admin         | POST   | http://localhost:9999/oauth/token | ❌ No         | Valid credentials         |
| Get Token - User (Write)  | POST   | http://localhost:9999/oauth/token | ❌ No         | Valid credentials         |
| Get Token - ReadOnly      | POST   | http://localhost:9999/oauth/token | ❌ No         | Valid credentials         |

---

## Environment Variables

The collection uses these variables:

| Variable        | Default Value          | Description                    |
|-----------------|------------------------|--------------------------------|
| `base_url`      | http://localhost:8080  | API service URL                |
| `oauth_url`     | http://localhost:9999  | OAuth2 server URL              |
| `access_token`  | (empty)                | Set this to your JWT token     |

You can override these in Postman's **Environment** or **Collection Variables**.
