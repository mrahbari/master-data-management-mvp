#!/bin/bash

echo ""
echo "========================================"
echo "  OAuth2 Token Generation Demo"
echo "========================================"
echo ""

OAUTH_URL="http://localhost:9999/oauth/token"

# Function to get token
get_token() {
  local username=$1
  local password=$2
  
  echo "🔐 Getting token for: $username"
  
  RESPONSE=$(curl -s -X POST "$OAUTH_URL" \
    -H "Content-Type: application/json" \
    -d "{\"grant_type\":\"password\",\"client_id\":\"mdm-client\",\"client_secret\":\"mdm-secret\",\"username\":\"$username\",\"password\":\"$password\"}")
  
  ACCESS_TOKEN=$(echo $RESPONSE | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
  
  if [ -n "$ACCESS_TOKEN" ]; then
    echo "✅ Token received!"
    echo ""
    echo "Access Token:"
    echo "$ACCESS_TOKEN"
    echo ""
    
    # Decode token payload
    PAYLOAD=$(echo $ACCESS_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null)
    echo "Token Claims:"
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

echo "========================================"
echo "  Token Usage Example"
echo "========================================"
echo ""
echo "curl -X POST http://localhost:8080/api/customers \\"
echo "  -H \"Authorization: Bearer <ACCESS_TOKEN>\" \\"
echo "  -H \"Content-Type: application/json\" \\"
echo "  -d '{\"email\": \"test@example.com\", \"sourceSystem\": \"test\"}'"
echo ""
