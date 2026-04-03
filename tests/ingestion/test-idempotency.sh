#!/bin/bash
# Test script for idempotent ingestion endpoint
# Usage: ./tests/ingestion/test-idempotency.sh [BASE_URL]

BASE_URL="${1:-http://localhost:8080}"
TOKEN="${2:-}"

echo "========================================="
echo "Idempotent Ingestion Endpoint Tests"
echo "========================================="
echo "Base URL: $BASE_URL"
echo ""

HEADERS="-H 'Content-Type: application/json'"
if [ -n "$TOKEN" ]; then
  HEADERS="$HEADERS -H 'Authorization: Bearer $TOKEN'"
fi

# Test 1: First request
echo "Test 1: First request (should return 202 Accepted)"
RESPONSE_1=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: test-key-001' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+15551234567",
    "sourceSystem": "CRM"
  }')

HTTP_CODE_1=$(echo "$RESPONSE_1" | tail -n1)
HEADERS_1=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: test-key-001' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+15551234567",
    "sourceSystem": "CRM"
  }')

EVENT_ID_1=$(echo "$HEADERS_1" | grep -i 'x-event-id' | tr -d '\r' | cut -d' ' -f2)

echo "HTTP Status: $HTTP_CODE_1"
echo "X-Event-ID: $EVENT_ID_1"
echo ""

# Test 2: Duplicate request
echo "Test 2: Duplicate request (should return 200 OK with X-Idempotency-Key: hit)"
RESPONSE_2=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: test-key-001' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+15551234567",
    "sourceSystem": "CRM"
  }')

HTTP_CODE_2=$(echo "$RESPONSE_2" | tail -n1)
HEADERS_2=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -H 'X-Idempotency-Key: test-key-001' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "email": "john.doe@example.com",
    "phone": "+15551234567",
    "sourceSystem": "CRM"
  }')

EVENT_ID_2=$(echo "$HEADERS_2" | grep -i 'x-event-id' | tr -d '\r' | cut -d' ' -f2)
IDEMPOTENCY_HEADER=$(echo "$HEADERS_2" | grep -i 'x-idempotency-replay' | tr -d '\r')

echo "HTTP Status: $HTTP_CODE_2"
echo "X-Event-ID: $EVENT_ID_2"
echo "Idempotency Header: $IDEMPOTENCY_HEADER"
echo ""

if [ "$EVENT_ID_1" = "$EVENT_ID_2" ]; then
  echo "✓ Event IDs match - idempotency working correctly"
else
  echo "✗ Event IDs do not match - idempotency issue detected"
fi
echo ""

# Test 3: Validation - missing nationalId
echo "Test 3: Missing nationalId (should return 400 Bad Request)"
RESPONSE_3=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "Jane Doe",
    "sourceSystem": "CRM"
  }')

HTTP_CODE_3=$(echo "$RESPONSE_3" | tail -n1)
echo "HTTP Status: $HTTP_CODE_3"
echo ""

# Test 4: Validation - invalid sourceSystem
echo "Test 4: Invalid sourceSystem (should return 400 Bad Request)"
RESPONSE_4=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -d '{
    "nationalId": "198011225359",
    "sourceSystem": "INVALID"
  }')

HTTP_CODE_4=$(echo "$RESPONSE_4" | tail -n1)
echo "HTTP Status: $HTTP_CODE_4"
echo ""

# Test 5: Deterministic key generation (no X-Idempotency-Key header)
echo "Test 5: Deterministic key generation (same payload twice, no header)"
RESPONSE_5A=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "sourceSystem": "CRM"
  }')

EVENT_ID_5A=$(echo "$RESPONSE_5A" | grep -i 'x-event-id' | tr -d '\r' | cut -d' ' -f2)

sleep 1

RESPONSE_5B=$(curl -s -D - -o /dev/null -X POST "$BASE_URL/api/customers" \
  -H 'Content-Type: application/json' \
  -d '{
    "nationalId": "198011225359",
    "name": "John Doe",
    "sourceSystem": "CRM"
  }')

EVENT_ID_5B=$(echo "$RESPONSE_5B" | grep -i 'x-event-id' | tr -d '\r' | cut -d' ' -f2)
IDEMPOTENCY_5B=$(echo "$RESPONSE_5B" | grep -i 'x-idempotency-replay' | tr -d '\r')

echo "First request X-Event-ID: $EVENT_ID_5A"
echo "Second request X-Event-ID: $EVENT_ID_5B"
echo "Second request Idempotency: $IDEMPOTENCY_5B"
echo ""

if [ "$EVENT_ID_5A" = "$EVENT_ID_5B" ]; then
  echo "✓ Deterministic key generation working correctly"
else
  echo "✗ Deterministic key generation issue detected"
fi

echo ""
echo "========================================="
echo "Tests Complete"
echo "========================================="
