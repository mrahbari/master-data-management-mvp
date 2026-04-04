#!/bin/bash

# Test script for DLQ Management endpoints

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

echo "✅ Token obtained"
echo ""

echo "=========================================="
echo "Test 1: GET /api/dlq/stats"
echo "=========================================="

RESPONSE1=$(curl -s "$BASE_URL_MASTERING/api/dlq/stats" \
  -H "Authorization: Bearer $TOKEN")

echo "$RESPONSE1" | python3 -m json.tool

if echo "$RESPONSE1" | python3 -c "import sys, json; data=json.load(sys.stdin); assert 'rawTopic' in data; assert 'dlqTopic' in data" 2>/dev/null; then
  echo "✅ DLQ stats endpoint working - topics loaded from configuration"
else
  echo "❌ DLQ stats endpoint failed or missing topic configuration"
fi

echo ""
echo "=========================================="
echo "Test 2: GET /api/dlq/structure"
echo "=========================================="

RESPONSE2=$(curl -s "$BASE_URL_MASTERING/api/dlq/structure" \
  -H "Authorization: Bearer $TOKEN")

echo "$RESPONSE2" | python3 -m json.tool | head -40

if echo "$RESPONSE2" | python3 -c "import sys, json; data=json.load(sys.stdin); assert 'example' in data" 2>/dev/null; then
  echo "✅ DLQ structure endpoint working"
else
  echo "❌ DLQ structure endpoint failed"
fi

echo ""
echo "=========================================="
echo "Test 3: GET /api/dlq/manual-reprocess"
echo "=========================================="

RESPONSE3=$(curl -s "$BASE_URL_MASTERING/api/dlq/manual-reprocess" \
  -H "Authorization: Bearer $TOKEN")

echo "$RESPONSE3" | python3 -m json.tool | head -30

if echo "$RESPONSE3" | python3 -c "import sys, json; data=json.load(sys.stdin); assert 'steps' in data" 2>/dev/null; then
  echo "✅ DLQ manual reprocess endpoint working"
else
  echo "❌ DLQ manual reprocess endpoint failed"
fi

echo ""
echo "=========================================="
echo "Test 4: POST /api/dlq/reprocess (with valid data)"
echo "=========================================="

RESPONSE4=$(curl -s -X POST "$BASE_URL_MASTERING/api/dlq/reprocess" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "originalEvent": {
      "eventId": "test-event-001",
      "nationalId": "123456789012",
      "name": "Test User",
      "email": "test@example.com",
      "phone": "+1-555-0000",
      "sourceSystem": "TEST",
      "timestamp": "2026-04-04T12:00:00Z"
    },
    "errorDetails": {
      "exception": "TestException",
      "message": "Test error message"
    },
    "processingHistory": [
      {
        "attempt": 1,
        "timestamp": "2026-04-04T12:00:01Z",
        "error": "Test error"
      }
    ],
    "schemaVersion": "v1"
  }')

echo "$RESPONSE4" | python3 -m json.tool

if echo "$RESPONSE4" | python3 -c "import sys, json; data=json.load(sys.stdin); assert data.get('success') == True; assert 'newEventId' in data" 2>/dev/null; then
  echo "✅ DLQ reprocess endpoint working - event republished to raw topic"
else
  echo "❌ DLQ reprocess endpoint failed"
fi

echo ""
echo "=========================================="
echo "Test 5: POST /api/dlq/reprocess (with invalid data - missing nationalId)"
echo "=========================================="

RESPONSE5=$(curl -s -X POST "$BASE_URL_MASTERING/api/dlq/reprocess" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "originalEvent": {
      "eventId": "test-event-002",
      "name": "Invalid User",
      "sourceSystem": "TEST"
    },
    "errorDetails": {
      "exception": "TestException",
      "message": "Test error"
    },
    "processingHistory": [],
    "schemaVersion": "v1"
  }')

echo "$RESPONSE5" | python3 -m json.tool

if echo "$RESPONSE5" | python3 -c "import sys, json; data=json.load(sys.stdin); assert data.get('success') == False; assert 'error' in data" 2>/dev/null; then
  echo "✅ DLQ reprocess validation working - properly rejected invalid data"
else
  echo "❌ DLQ reprocess validation failed"
fi

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "All DLQ management endpoints have been tested."
echo "Check the results above to ensure all tests passed."
echo ""
echo "The topic names are now loaded from configuration:"
echo "  - Raw topic: \${kafka.topics.customer-raw:customer.raw}"
echo "  - DLQ topic: \${kafka.topics.customer-dlq:customer.dlq}"
echo "=========================================="
