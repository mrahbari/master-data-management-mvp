#!/bin/bash

# Test script for idempotency fix
# Tests that different X-Idempotency-Key values allow multiple requests
# with the same nationalId + sourceSystem

BASE_URL="http://localhost:8080"
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
  exit 1
fi

echo "✅ Token obtained"
echo ""

echo "=========================================="
echo "Test 1: First request with unique key"
echo "X-Idempotency-Key: test-key-001"
echo "nationalId: 111222333444, source: GOVERNMENT"
echo "=========================================="

RESPONSE1=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: test-key-001" \
  -d '{
    "nationalId": "111222333444",
    "name": "Bob Williams Me",
    "email": "bob@example.com",
    "phone": "+1-555-3333",
    "sourceSystem": "GOVERNMENT"
  }')

HTTP1=$(echo "$RESPONSE1" | tail -1)
BODY1=$(echo "$RESPONSE1" | head -n -1)
EVENT_ID1=$(echo "$RESPONSE1" | grep -i "x-event-id" | head -1)

echo "HTTP Status: $HTTP1"
echo "Event ID Header: $EVENT_ID1"
echo "Response: $BODY1"

if [ "$HTTP1" = "202" ]; then
  echo "✅ Request 1 accepted (202)"
else
  echo "❌ Request 1 failed - expected 202, got $HTTP1"
fi

echo ""
echo "=========================================="
echo "Test 2: Second request with DIFFERENT key"
echo "X-Idempotency-Key: test-key-002 (different)"
echo "nationalId: 111222333444, source: GOVERNMENT (same)"
echo "=========================================="

RESPONSE2=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: test-key-002" \
  -d '{
    "nationalId": "111222333444",
    "name": "Bob Williams Me Updated",
    "email": "bob.updated@example.com",
    "phone": "+1-555-4444",
    "sourceSystem": "GOVERNMENT"
  }')

HTTP2=$(echo "$RESPONSE2" | tail -1)
BODY2=$(echo "$RESPONSE2" | head -n -1)
EVENT_ID2=$(echo "$RESPONSE2" | grep -i "x-event-id" | head -1)

echo "HTTP Status: $HTTP2"
echo "Event ID Header: $EVENT_ID2"
echo "Response: $BODY2"

if [ "$HTTP2" = "202" ]; then
  echo "✅ Request 2 accepted (202) - Idempotency fix working!"
elif [ "$HTTP2" = "200" ]; then
  IDEMPOTENCY_REPLAY=$(echo "$RESPONSE2" | grep -i "x-idempotency-replay")
  if [ -n "$IDEMPOTENCY_REPLAY" ]; then
    echo "⚠️  Request 2 returned 200 (Cached) - Idempotency fix NOT working"
  else
    echo "✅ Request 2 accepted (200 with replay)"
  fi
else
  echo "❌ Request 2 failed - expected 202, got $HTTP2"
fi

echo ""
echo "=========================================="
echo "Test 3: Retry request 1 with SAME key"
echo "X-Idempotency-Key: test-key-001 (same as Test 1)"
echo "=========================================="

RESPONSE3=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Idempotency-Key: test-key-001" \
  -d '{
    "nationalId": "111222333444",
    "name": "Bob Williams Me",
    "email": "bob@example.com",
    "phone": "+1-555-3333",
    "sourceSystem": "GOVERNMENT"
  }')

HTTP3=$(echo "$RESPONSE3" | tail -1)
BODY3=$(echo "$RESPONSE3" | head -n -1)
EVENT_ID3=$(echo "$RESPONSE3" | grep -i "x-event-id" | head -1)
IDEMPOTENCY_REPLAY3=$(echo "$RESPONSE3" | grep -i "x-idempotency-replay")

echo "HTTP Status: $HTTP3"
echo "Event ID Header: $EVENT_ID3"
echo "Idempotency-Replay Header: $IDEMPOTENCY_REPLAY3"
echo "Response: $BODY3"

if [ "$HTTP3" = "200" ] && [ -n "$IDEMPOTENCY_REPLAY3" ]; then
  echo "✅ Request 3 cached (200 with X-Idempotency-Replay) - Idempotency working correctly!"
else
  echo "⚠️  Request 3 unexpected response - expected 200 with replay header"
fi

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo "Request 1 (test-key-001): $HTTP1 - Expected: 202"
echo "Request 2 (test-key-002): $HTTP2 - Expected: 202 (different key = new request)"
echo "Request 3 (test-key-001): $HTTP3 - Expected: 200 (same key = cached response)"
echo ""
if [ "$HTTP1" = "202" ] && [ "$HTTP2" = "202" ] && [ "$HTTP3" = "200" ]; then
  echo "✅ ALL TESTS PASSED - Idempotency fix is working correctly!"
else
  echo "❌ Some tests failed - check the output above"
fi
echo "=========================================="
