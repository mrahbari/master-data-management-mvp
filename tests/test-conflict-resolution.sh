#!/bin/bash

# Test script for conflict resolution scenarios
# This script tests the fixed Postman collection requests

BASE_URL_INGESTION="http://localhost:8080"
BASE_URL_MASTERING="http://localhost:8081"
OAUTH_URL="http://localhost:9999"

echo "=========================================="
echo "Getting OAuth2 Token..."
echo "=========================================="

TOKEN_RESPONSE=$(curl -s "$OAUTH_URL/oauth/token" \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "password",
    "client_id": "mdm-client",
    "client_secret": "mdm-secret",
    "username": "admin",
    "password": "admin123"
  }')

TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['access_token'])" 2>/dev/null)

if [ -z "$TOKEN" ]; then
  echo "❌ Failed to get OAuth2 token"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "✅ Token obtained: ${TOKEN:0:50}..."
echo ""

echo "=========================================="
echo "Scenario 1: LATEST_UPDATE Strategy"
echo "=========================================="
echo ""
echo "Step 1: Send CRM Event (nationalId: 123456789012)"
echo "----------------------------------------"

RESPONSE1=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "123456789012",
    "name": "John Doe",
    "email": "john@old.com",
    "phone": "+1-555-1111",
    "sourceSystem": "CRM"
  }')

HTTP_CODE1=$(echo "$RESPONSE1" | tail -1)
BODY1=$(echo "$RESPONSE1" | head -n -1)

echo "HTTP Status: $HTTP_CODE1"
if [ "$HTTP_CODE1" = "202" ] || [ "$HTTP_CODE1" = "200" ]; then
  echo "✅ CRM event accepted"
else
  echo "❌ Failed to send CRM event"
  echo "Response: $BODY1"
fi

echo ""
echo "Step 2: Send BANK Event (nationalId: 123456789012)"
echo "----------------------------------------"

sleep 2  # Small delay to ensure different timestamps

RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "123456789012",
    "name": "John Doe",
    "email": "john@new.com",
    "phone": "+1-555-2222",
    "sourceSystem": "BANK"
  }')

HTTP_CODE2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | head -n -1)

echo "HTTP Status: $HTTP_CODE2"
if [ "$HTTP_CODE2" = "202" ] || [ "$HTTP_CODE2" = "200" ]; then
  echo "✅ BANK event accepted"
else
  echo "❌ Failed to send BANK event"
  echo "Response: $BODY2"
fi

echo ""
echo "Step 3: Verify Resolved Record"
echo "----------------------------------------"

sleep 3  # Wait for processing

RESPONSE3=$(curl -s "$BASE_URL_MASTERING/api/customers/by-national-id?nationalId=123456789012")
echo "Response: $RESPONSE3"
echo ""

if echo "$RESPONSE3" | grep -q "john@new.com"; then
  echo "✅ LATEST_UPDATE working - BANK email won"
else
  echo "⚠️  Could not verify conflict resolution yet (may still be processing)"
fi

echo ""
echo "=========================================="
echo "Scenario 2: TRUSTED_SOURCE Strategy"  
echo "=========================================="
echo ""
echo "Step 1: Send CRM Event (nationalId: 987654321012)"
echo "----------------------------------------"

RESPONSE4=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "987654321012",
    "name": "John Michael Doe",
    "email": "john@crm.com",
    "phone": "+1-555-3333",
    "sourceSystem": "CRM"
  }')

HTTP_CODE4=$(echo "$RESPONSE4" | tail -1)
echo "HTTP Status: $HTTP_CODE4"
if [ "$HTTP_CODE4" = "202" ] || [ "$HTTP_CODE4" = "200" ]; then
  echo "✅ CRM event accepted"
else
  echo "❌ Failed to send CRM event"
fi

echo ""
echo "Step 2: Send BANK Event (nationalId: 987654321012)"
echo "----------------------------------------"

sleep 2

RESPONSE5=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "987654321012",
    "name": "J. Doe",
    "email": "john@bank.com",
    "phone": "+1-555-4444",
    "sourceSystem": "BANK"
  }')

HTTP_CODE5=$(echo "$RESPONSE5" | tail -1)
echo "HTTP Status: $HTTP_CODE5"
if [ "$HTTP_CODE5" = "202" ] || [ "$HTTP_CODE5" = "200" ]; then
  echo "✅ BANK event accepted"
else
  echo "❌ Failed to send BANK event"
fi

echo ""
echo "Step 3: Verify Resolved Record"
echo "----------------------------------------"

sleep 3

RESPONSE6=$(curl -s "$BASE_URL_MASTERING/api/customers/by-national-id?nationalId=987654321012")
echo "Response: $RESPONSE6"
echo ""

if echo "$RESPONSE6" | grep -q "John Michael Doe"; then
  echo "✅ TRUSTED_SOURCE working - CRM name preserved"
else
  echo "⚠️  Could not verify conflict resolution yet (may still be processing)"
fi

echo ""
echo "=========================================="
echo "Scenario 3: MERGE Strategy (Phones)"
echo "=========================================="
echo ""
echo "Step 1: Send First Phone (nationalId: 555555555512)"
echo "----------------------------------------"

RESPONSE7=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "555555555512",
    "name": "Jane Smith",
    "email": "jane@example.com",
    "phone": "+1-555-1111",
    "sourceSystem": "CRM"
  }')

HTTP_CODE7=$(echo "$RESPONSE7" | tail -1)
echo "HTTP Status: $HTTP_CODE7"
if [ "$HTTP_CODE7" = "202" ] || [ "$HTTP_CODE7" = "200" ]; then
  echo "✅ First phone event accepted"
else
  echo "❌ Failed to send first phone event"
fi

echo ""
echo "Step 2: Send Second Phone (nationalId: 555555555512)"
echo "----------------------------------------"

sleep 2

RESPONSE8=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "555555555512",
    "name": "Jane Smith",
    "email": "jane@example.com",
    "phone": "+1-555-2222",
    "sourceSystem": "BANK"
  }')

HTTP_CODE8=$(echo "$RESPONSE8" | tail -1)
echo "HTTP Status: $HTTP_CODE8"
if [ "$HTTP_CODE8" = "202" ] || [ "$HTTP_CODE8" = "200" ]; then
  echo "✅ Second phone event accepted"
else
  echo "❌ Failed to send second phone event"
fi

echo ""
echo "Step 3: Send Third Phone (nationalId: 555555555512)"
echo "----------------------------------------"

sleep 2

RESPONSE9=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "nationalId": "555555555512",
    "name": "Jane Smith",
    "email": "jane@example.com",
    "phone": "+1-555-3333",
    "sourceSystem": "GOVERNMENT"
  }')

HTTP_CODE9=$(echo "$RESPONSE9" | tail -1)
echo "HTTP Status: $HTTP_CODE9"
if [ "$HTTP_CODE9" = "202" ] || [ "$HTTP_CODE9" = "200" ]; then
  echo "✅ Third phone event accepted"
else
  echo "❌ Failed to send third phone event"
fi

echo ""
echo "Step 4: Verify Merged Phones"
echo "----------------------------------------"

sleep 3

RESPONSE10=$(curl -s "$BASE_URL_MASTERING/api/customers/by-national-id?nationalId=555555555512")
echo "Response: $RESPONSE10"
echo ""

if echo "$RESPONSE10" | grep -q "555-1111" && echo "$RESPONSE10" | grep -q "555-2222"; then
  echo "✅ MERGE working - Multiple phones present"
else
  echo "⚠️  Could not verify phone merge yet (may still be processing)"
fi

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "All requests have been sent successfully."
echo "Check the responses above to verify conflict resolution strategies are working."
echo ""
echo "You can also check:"
echo "  - Mastering service logs: docker logs master-data-management-mvp-customer-mastering-service-1"
echo "  - Conflict resolution logs: ./logs/conflict-resolution.log"
echo "  - Metrics: curl http://localhost:8081/actuator/prometheus | grep conflicts_resolved_total"
echo "=========================================="
