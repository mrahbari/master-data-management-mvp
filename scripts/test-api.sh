#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Default host and port
HOST="${1:-localhost}"
INGESTION_PORT="${2:-8080}"
MASTERING_PORT="${3:-8081}"

BASE_URL="http://$HOST:$INGESTION_PORT"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MDM MVP - API Test Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
}

print_error() {
    echo -e "${RED}[FAIL]${NC} $1"
}

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Test function
test_api() {
    local name="$1"
    local method="$2"
    local endpoint="$3"
    local expected_status="$4"
    local data="$5"
    
    echo -n "Testing: $name... "
    
    if [ "$method" == "POST" ] && [ -n "$data" ]; then
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$endpoint" 2>/dev/null)
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" \
            "$BASE_URL$endpoint" 2>/dev/null)
    fi
    
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | head -n -1)
    
    if [ "$http_code" == "$expected_status" ]; then
        print_success "$http_code"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        print_error "Expected $expected_status, got $http_code"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# Check service health
check_health() {
    print_status "Checking service health..."
    
    # Check ingestion service
    echo -n "  Ingestion Service ($BASE_URL/actuator/health)... "
    if curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        print_success "UP"
    else
        print_error "DOWN"
        echo ""
        print_error "Ingestion service is not responding. Make sure it's running."
        exit 1
    fi
    
    # Check mastering service
    echo -n "  Mastering Service (http://$HOST:$MASTERING_PORT/actuator/health)... "
    if curl -s -f "http://$HOST:$MASTERING_PORT/actuator/health" > /dev/null 2>&1; then
        print_success "UP"
    else
        print_error "DOWN"
        print_status "Mastering service is not responding. Make sure it's running."
    fi
    
    echo ""
}

# Run API tests
run_tests() {
    echo -e "${YELLOW}Running API Tests${NC}"
    echo ""
    
    # Test 1: Health endpoint
    test_api "Health Check" "GET" "/actuator/health" "200"
    
    # Test 2: Missing content type
    test_api "POST without Content-Type" "POST" "/api/customers" "415"
    
    # Test 3: Missing required fields
    test_api "POST with empty body" "POST" "/api/customers" "400" "{}"
    
    # Test 4: Missing email
    test_api "POST without email" "POST" "/api/customers" "400" '{"sourceSystem": "test"}'
    
    # Test 5: Invalid email format
    test_api "POST with invalid email" "POST" "/api/customers" "400" '{"email": "invalid", "sourceSystem": "test"}'
    
    # Test 6: Valid customer - first submission
    print_status "Submitting new customer (john.doe@example.com)..."
    test_api "POST valid customer #1" "POST" "/api/customers" "202" \
        '{"email": "john.doe@example.com", "firstName": "John", "lastName": "Doe", "phone": "+1-555-1111", "sourceSystem": "test"}'
    
    # Test 7: Same customer with different case (should be duplicate)
    print_status "Submitting duplicate customer (JOHN.DOE@EXAMPLE.COM)..."
    test_api "POST duplicate customer #2" "POST" "/api/customers" "202" \
        '{"email": "JOHN.DOE@EXAMPLE.COM", "firstName": "Johnny", "lastName": "Doe", "phone": "+1-555-2222", "sourceSystem": "test"}'
    
    # Test 8: Customer with whitespace in email
    print_status "Submitting customer with whitespace (  John.Doe@Example.com  )..."
    test_api "POST customer with whitespace #3" "POST" "/api/customers" "202" \
        '{"email": "  John.Doe@Example.com  ", "firstName": "John", "lastName": "Doe", "phone": "+1-555-3333", "sourceSystem": "test"}'
    
    # Test 9: Different customer
    print_status "Submitting different customer (jane.smith@example.com)..."
    test_api "POST different customer #4" "POST" "/api/customers" "202" \
        '{"email": "jane.smith@example.com", "firstName": "Jane", "lastName": "Smith", "phone": "+1-555-4444", "sourceSystem": "test"}'
    
    # Test 10: Metrics endpoint
    test_api "Metrics endpoint" "GET" "/actuator/prometheus" "200"
    
    echo ""
}

# Check metrics
check_metrics() {
    print_status "Fetching metrics..."
    echo ""
    
    metrics=$(curl -s "$BASE_URL/actuator/prometheus" 2>/dev/null || echo "")
    
    if [ -n "$metrics" ]; then
        echo -e "${YELLOW}Ingestion Service Metrics:${NC}"
        echo "$metrics" | grep -E "^http_server_requests_seconds_count" | head -3 || print_status "No HTTP metrics yet"
        echo ""
    fi
    
    mastering_metrics=$(curl -s "http://$HOST:$MASTERING_PORT/actuator/prometheus" 2>/dev/null || echo "")
    
    if [ -n "$mastering_metrics" ]; then
        echo -e "${YELLOW}Mastering Service Metrics:${NC}"
        echo "$mastering_metrics" | grep -E "(processed_events_total|duplicates_detected_total|golden_records_created_total)" || print_status "No processing metrics yet"
        echo ""
    fi
}

# Show summary
show_summary() {
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  Test Summary${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    echo -e "  ${GREEN}Passed:${NC} $TESTS_PASSED"
    echo -e "  ${RED}Failed:${NC} $TESTS_FAILED"
    echo ""
    
    if [ $TESTS_FAILED -eq 0 ]; then
        print_success "All tests passed!"
    else
        print_error "Some tests failed"
    fi
    
    echo ""
    print_status "Wait a few seconds for async processing, then check mastering service metrics:"
    echo "  curl http://$HOST:$MASTERING_PORT/actuator/prometheus | grep -E '(processed|duplicates|golden)'"
    echo ""
}

# Main
main() {
    check_health
    run_tests
    check_metrics
    show_summary
    
    # Exit with error if any tests failed
    if [ $TESTS_FAILED -gt 0 ]; then
        exit 1
    fi
}

main
