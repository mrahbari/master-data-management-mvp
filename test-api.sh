#!/bin/bash

# =============================================================================
# Quick Test Script for MDM MVP API
# =============================================================================
# Usage: ./test-api.sh
# 
# This script:
# 1. Gets a fresh token from OAuth2 server
# 2. Tests the API endpoints
# 3. Reports any issues
# =============================================================================

set -e

OAUTH_URL="http://localhost:9999"
API_URL="http://localhost:8080"

echo ""
echo "========================================"
echo "  MDM API Quick Test"
echo "========================================"
echo ""

# Step 1: Check if services are running
echo "🔍 Step 1: Checking services..."

# Check OAuth2 server
if curl -s -o /dev/null -w "%{http_code}" "$OAUTH_URL/.well-known/openid-configuration" | grep -q "200"; then
  echo "  ✅ OAuth2 Server: Running ($OAUTH_URL)"
else
  echo "  ❌ OAuth2 Server: NOT running"
  echo "     Start with: docker-compose up -d oauth-server"
  exit 1
fi

# Check API service
if curl -s -o /dev/null -w "%{http_code}" "$API_URL/actuator/health" | grep -q "200"; then
  echo "  ✅ API Service: Running ($API_URL)"
else
  echo "  ❌ API Service: NOT running"
  echo "     Start with: docker-compose up -d customer-ingestion-service"
  exit 1
fi

echo ""

# Step 2: Get admin token
echo "🔐 Step 2: Getting admin token..."
TOKEN_RESPONSE=$(curl -s -X POST "$OAUTH_URL/oauth/token" \
  -H "Content-Type: application/json" \
  -d '{"grant_type":"password","client_id":"mdm-client","client_secret":"mdm-secret","username":"admin","password":"admin123"}')

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "❌ Failed to get token!"
  echo "Response: $TOKEN_RESPONSE"
  exit 1
fi

echo "✅ Token received"
echo ""

# Step 3: Test health endpoint (no auth)
echo "📋 Step 3: Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/actuator/health")
HEALTH_STATUS=$(echo "$HEALTH_RESPONSE" | head -n 1 | grep -o '"status":"[^"]*' | cut -d'"' -f4)
HEALTH_CODE=$(echo "$HEALTH_RESPONSE" | tail -n 1)

if [ "$HEALTH_CODE" = "200" ]; then
  echo "  ✅ Health check passed (Status: $HEALTH_STATUS)"
else
  echo "  ❌ Health check failed (HTTP: $HEALTH_CODE)"
  echo "  Response: $HEALTH_RESPONSE"
fi

echo ""

# Step 4: Test API endpoint with token
echo "📋 Step 4: Testing authenticated endpoint..."
echo "  POST $API_URL/api/customers"

API_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/api/customers" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"email": "test-'$(date +%s)'@example.com", "sourceSystem": "quick-test"}')

API_CODE=$(echo "$API_RESPONSE" | tail -n 1)
API_BODY=$(echo "$API_RESPONSE" | head -n -1)

if [ "$API_CODE" = "200" ] || [ "$API_CODE" = "201" ]; then
  echo "  ✅ API call successful (HTTP: $API_CODE)"
  echo ""
  echo "  Response:"
  echo "$API_BODY" | python3 -m json.tool 2>/dev/null || echo "$API_BODY"
else
  echo "  ❌ API call failed (HTTP: $API_CODE)"
  echo ""
  echo "  Response:"
  echo "$API_BODY" | python3 -m json.tool 2>/dev/null || echo "$API_BODY"
  echo ""
  echo "  💡 Troubleshooting:"
  echo "     - Check token issuer matches: echo '$ACCESS_TOKEN' | cut -d'.' -f2 | base64 -d"
  echo "     - Expected issuer: http://host.docker.internal:9999"
  echo "     - Check Spring logs: docker logs <container-name>"
fi

echo ""

# Step 5: Test GET customers
echo "📋 Step 5: Testing GET customers..."
GET_RESPONSE=$(curl -s -w "\n%{http_code}" "$API_URL/api/customers" \
  -H "Authorization: Bearer $ACCESS_TOKEN")

GET_CODE=$(echo "$GET_RESPONSE" | tail -n 1)
GET_BODY=$(echo "$GET_RESPONSE" | head -n -1)

if [ "$GET_CODE" = "200" ]; then
  echo "  ✅ GET customers successful (HTTP: $GET_CODE)"
else
  echo "  ❌ GET customers failed (HTTP: $GET_CODE)"
  echo "  Response: $GET_BODY" | python3 -m json.tool 2>/dev/null || echo "  Response: $GET_BODY"
fi

echo ""
echo "========================================"
echo "  Test Complete"
echo "========================================"
echo ""
echo "💡 To use in Postman:"
echo "   1. Import: postman-collection.json"
echo "   2. Set collection variable 'access_token' to:"
echo "      $ACCESS_TOKEN"
echo "   3. Run the requests!"
echo ""
