#!/bin/bash

# Test script for out-of-order event handling (Task 002)
# Tests stale event detection, version tracking, and optimistic locking

BASE_URL_INGESTION="http://localhost:8080"
BASE_URL_MASTERING="http://localhost:8081"
OAUTH_URL="http://localhost:9999"
NATIONAL_ID="9876543210"

echo "=========================================="
echo "Out-of-Order Event Handling Tests (Task 002)"
echo "=========================================="

# Get OAuth2 token
echo ""
echo "Getting OAuth2 Token..."
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
echo "✅ Token acquired"
echo ""

# Helper function to send events to ingestion service
send_event() {
  local event_version=$1
  local name=$2
  local email=$3
  local phone=$4
  local timestamp=$5

  echo "  Sending event: version=$event_version, name=$name, email=$email"

  curl -s -X POST "$BASE_URL_INGESTION/api/customers" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $TOKEN" \
    -d "{
      \"nationalId\": \"$NATIONAL_ID\",
      \"name\": \"$name\",
      \"email\": \"$email\",
      \"phone\": \"$phone\",
      \"sourceSystem\": \"TEST\",
      \"eventVersion\": $event_version,
      \"timestamp\": \"$timestamp\"
    }"
  echo ""
}

# ==========================================
# Test 1: In-order events
# ==========================================
echo "=========================================="
echo "Test 1: In-order events (v1 → v2 → v3)"
echo "=========================================="

send_event 1 "Alice v1" "alice1@test.com" "+1111111111" "2026-01-01T10:00:00Z"
sleep 2
send_event 2 "Alice v2" "alice2@test.com" "+2222222222" "2026-01-01T10:01:00Z"
sleep 2
send_event 3 "Alice v3" "alice3@test.com" "+3333333333" "2026-01-01T10:02:00Z"
sleep 3

# Query the golden record
echo "  Querying golden record..."
GOLDEN=$(curl -s "$BASE_URL_MASTERING/api/customers/$NATIONAL_ID" \
  -H "Authorization: Bearer $TOKEN")

echo "  Golden record: $GOLDEN"
echo "  ✅ Test 1 complete — check that version=3 and latest values are present"
echo ""

# ==========================================
# Test 2: Out-of-order events (v3 arrives before v2)
# ==========================================
echo "=========================================="
echo "Test 2: Out-of-order events (v1 → v4 → v2)"
echo "  v2 should be detected as stale"
echo "=========================================="

NEW_NATIONAL_ID="1122334455"

# Send v1
echo "  Sending event v1..."
curl -s -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"nationalId\": \"$NEW_NATIONAL_ID\",
    \"name\": \"Bob v1\",
    \"email\": \"bob1@test.com\",
    \"phone\": \"+4444444444\",
    \"sourceSystem\": \"TEST\",
    \"eventVersion\": 1,
    \"timestamp\": \"2026-01-01T11:00:00Z\"
  }" > /dev/null
echo "    ✅ v1 sent"
sleep 2

# Send v4 (out of order — skips v2, v3)
echo "  Sending event v4 (out of order)..."
curl -s -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"nationalId\": \"$NEW_NATIONAL_ID\",
    \"name\": \"Bob v4\",
    \"email\": \"bob4@test.com\",
    \"phone\": \"+5555555555\",
    \"sourceSystem\": \"TEST\",
    \"eventVersion\": 4,
    \"timestamp\": \"2026-01-01T11:03:00Z\"
  }" > /dev/null
echo "    ✅ v4 sent"
sleep 3

# Send v2 (this should be detected as stale)
echo "  Sending event v2 (should be stale)..."
curl -s -X POST "$BASE_URL_INGESTION/api/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{
    \"nationalId\": \"$NEW_NATIONAL_ID\",
    \"name\": \"Bob v2 (stale)\",
    \"email\": \"bob2-stale@test.com\",
    \"phone\": \"+6666666666\",
    \"sourceSystem\": \"TEST\",
    \"eventVersion\": 2,
    \"timestamp\": \"2026-01-01T11:01:00Z\"
  }" > /dev/null
echo "    ✅ v2 sent (should be detected as stale)"
sleep 3

# Query the golden record — should still have v4 data, NOT v2
echo "  Querying golden record..."
GOLDEN2=$(curl -s "$BASE_URL_MASTERING/api/customers/$NEW_NATIONAL_ID" \
  -H "Authorization: Bearer $TOKEN")

echo "  Golden record: $GOLDEN2"
echo "  ✅ Test 2 complete — check that stale v2 did NOT override v4 data"
echo ""

# ==========================================
# Check stale events log
# ==========================================
echo "=========================================="
echo "Checking stale events log..."
echo "=========================================="

if [ -f "./logs/stale-events.log" ]; then
  echo "✅ stale-events.log exists"
  echo "  Last 10 entries:"
  tail -10 ./logs/stale-events.log | sed 's/^/    /'
else
  echo "⚠️  stale-events.log not found at ./logs/stale-events.log"
fi
echo ""

# ==========================================
# Check metrics
# ==========================================
echo "=========================================="
echo "Checking metrics (stale_events_total, optimistic_lock_failures_total)..."
echo "=========================================="

METRICS=$(curl -s "$BASE_URL_MASTERING/actuator/metrics/stale_events_total" 2>/dev/null)
if [ -n "$METRICS" ]; then
  echo "✅ stale_events_total metric: $METRICS"
else
  echo "⚠️  stale_events_total metric not available"
fi

METRICS2=$(curl -s "$BASE_URL_MASTERING/actuator/metrics/optimistic_lock_failures_total" 2>/dev/null)
if [ -n "$METRICS2" ]; then
  echo "✅ optimistic_lock_failures_total metric: $METRICS2"
else
  echo "⚠️  optimistic_lock_failures_total metric not available"
fi
echo ""

echo "=========================================="
echo "All out-of-order event tests complete!"
echo "=========================================="
echo ""
echo "Manual verification checklist:"
echo "  [ ] Golden record version incremented on every update"
echo "  [ ] Older events do NOT override newer master data"
echo "  [ ] Stale events logged to ./logs/stale-events.log"
echo "  [ ] Metrics available at /actuator/metrics endpoint"
