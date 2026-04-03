#!/bin/bash

# =============================================================================
# OAuth2 Token Generation Script for MDM MVP
# =============================================================================
# Usage: ./get-tokens.sh
# 
# This script retrieves JWT tokens from the local OAuth2 server running in Docker.
# Tokens are saved to jwt-tokens.json for use in Postman or curl commands.
# =============================================================================

set -e

OAUTH_URL="http://localhost:9999/oauth/token"
API_BASE_URL="http://localhost:8080"
TOKEN_FILE="jwt-tokens.json"

echo ""
echo "========================================"
echo "  OAuth2 Token Generation"
echo "========================================"
echo ""

# Check if OAuth server is running
echo "🔍 Checking OAuth2 server..."
if ! curl -s -o /dev/null -w "%{http_code}" "http://localhost:9999/.well-known/openid-configuration" | grep -q "200"; then
  echo "❌ OAuth2 server is not running at http://localhost:9999"
  echo ""
  echo "Please start Docker services:"
  echo "  docker-compose up -d oauth-server"
  echo ""
  echo "Or check if container is running:"
  echo "  docker ps | grep oauth-server"
  exit 1
fi
echo "✅ OAuth2 server is running"
echo ""

# Initialize JSON output
declare -A TOKENS

# Function to get token
get_token() {
  local username=$1
  local password=$2

  echo "🔐 Getting token for: $username"

  RESPONSE=$(curl -s -X POST "$OAUTH_URL" \
    -H "Content-Type: application/json" \
    -d "{\"grant_type\":\"password\",\"client_id\":\"mdm-client\",\"client_secret\":\"mdm-secret\",\"username\":\"$username\",\"password\":\"$password\"}")

  ACCESS_TOKEN=$(echo "$RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

  if [ -n "$ACCESS_TOKEN" ]; then
    echo "✅ Token received!"
    TOKENS[$username]="$ACCESS_TOKEN"
    echo ""
    
    # Decode and display token claims
    echo "📋 Token Claims:"
    PAYLOAD=$(echo "$ACCESS_TOKEN" | cut -d'.' -f2 | base64 -d 2>/dev/null)
    echo "$PAYLOAD" | python3 -m json.tool 2>/dev/null || echo "$PAYLOAD"
    echo ""
  else
    echo "❌ Failed to get token"
    echo "Response: $RESPONSE"
  fi

  echo "----------------------------------------"
  echo ""
}

# Get tokens for all users
get_token "admin" "admin123"
get_token "user" "user123"
get_token "readonly" "readonly123"

# Save tokens to JSON file
echo "{" > "$TOKEN_FILE"
echo "  \"admin\": \"${TOKENS[admin]}\"," >> "$TOKEN_FILE"
echo "  \"user\": \"${TOKENS[user]}\"," >> "$TOKEN_FILE"
echo "  \"readonly\": \"${TOKENS[readonly]}\"," >> "$TOKEN_FILE"
echo "  \"generated_at\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\"," >> "$TOKEN_FILE"
echo "  \"expires_in\": 3600," >> "$TOKEN_FILE"
echo "  \"oauth_server\": \"http://localhost:9999\"," >> "$TOKEN_FILE"
echo "  \"api_base_url\": \"$API_BASE_URL\"" >> "$TOKEN_FILE"
echo "}" >> "$TOKEN_FILE"

echo "✅ Tokens saved to $TOKEN_FILE"
echo ""

# Display usage examples
echo "========================================"
echo "  Postman Configuration"
echo "========================================"
echo ""
echo "1️⃣  Import Collection (or manually configure):"
echo "   - Base URL: $API_BASE_URL"
echo "   - All /api/* endpoints require Bearer token"
echo ""
echo "2️⃣  Authorization Tab:"
echo "   - Type: Bearer Token"
echo "   - Token: Copy from $TOKEN_FILE"
echo ""
echo "3️⃣  Quick Test (Admin Token):"
echo "   curl -X POST $API_BASE_URL/api/customers \\"
echo "     -H \"Authorization: Bearer ${TOKENS[admin]}\" \\"
echo "     -H \"Content-Type: application/json\" \\"
echo "     -d '{\"email\": \"test@example.com\", \"sourceSystem\": \"postman-test\"}'"
echo ""
echo "========================================"
echo ""
