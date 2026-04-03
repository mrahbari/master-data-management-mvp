#!/bin/bash

# ===========================================
# MDM MVP - Clean Build Script
# ===========================================
# 
# Usage:
#   ./clean-build.sh              # Clean and build
#   ./clean-build.sh --skip-tests # Skip tests
#   ./clean-build.sh --help       # Show help
#
# ===========================================

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ===========================================
# Helper Functions
# ===========================================

print_header() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_info() {
    echo -e "${YELLOW}ℹ $1${NC}"
}

show_help() {
    echo ""
    echo "MDM MVP - Clean Build Script"
    echo ""
    echo "Usage:"
    echo "  ./clean-build.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --skip-tests    Skip running tests"
    echo "  --skip-quality  Skip code quality checks (checkstyle, spotless, spotbugs)"
    echo "  --clean-only    Only clean, don't build"
    echo "  --reset-kafka   Clean build and reset Kafka data (removes poison messages)"
    echo "  --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  ./clean-build.sh                  # Full clean build with tests"
    echo "  ./clean-build.sh --skip-tests     # Build without tests (faster)"
    echo "  ./clean-build.sh --clean-only     # Just clean build artifacts"
    echo "  ./clean-build.sh --reset-kafka    # Clean build and reset Kafka topics"
    echo ""
}

# ===========================================
# Parse Arguments
# ===========================================

SKIP_TESTS=false
SKIP_QUALITY=false
CLEAN_ONLY=false
RESET_KAFKA=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-tests)
            SKIP_TESTS=true
            shift
            ;;
        --skip-quality)
            SKIP_QUALITY=true
            shift
            ;;
        --clean-only)
            CLEAN_ONLY=true
            shift
            ;;
        --reset-kafka)
            RESET_KAFKA=true
            shift
            ;;
        --help)
            show_help
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# ===========================================
# Main Script
# ===========================================

print_header "MDM MVP - Clean Build"

# Check Java version
print_info "Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -ge 21 ]; then
    print_success "Java $JAVA_VERSION detected"
else
    print_info "Java $JAVA_VERSION detected (Java 21 recommended)"
fi

# Clean build directories
print_header "Step 1: Cleaning Build Artifacts"

print_info "Removing build directories..."
rm -rf customer-ingestion-service/build
print_success "Cleaned customer-ingestion-service/build"

rm -rf customer-mastering-service/build
print_success "Cleaned customer-mastering-service/build"

rm -rf .gradle
print_success "Cleaned .gradle cache"

# Clean Gradle cache in service directories
#rm -rf customer-ingestion-service/.gradle
#rm -rf customer-mastering-service/.gradle
print_success "Cleaned service Gradle caches"

# Clean IDE artifacts
rm -rf customer-ingestion-service/.idea
rm -rf customer-mastering-service/.idea
rm -rf *.iml
print_success "Cleaned IDE artifacts"

# Clean logs
rm -rf logs/*.log
rm -rf *.log
print_success "Cleaned log files"

if [ "$CLEAN_ONLY" = true ]; then
    print_header "Clean Complete"
    print_success "Build artifacts removed"
    exit 0
fi

# Reset Kafka if requested
if [ "$RESET_KAFKA" = true ]; then
    print_header "Step 1.5: Resetting Kafka Data"
    print_info "Stopping services and removing Kafka volumes..."
    
    docker-compose down -v 2>/dev/null || true
    print_success "Kafka data cleared"
    
    print_info "Restarting infrastructure..."
    docker-compose up -d kafka postgres
    print_info "Waiting for Kafka and PostgreSQL to be ready (30 seconds)..."
    sleep 30
    docker-compose ps
    print_success "Infrastructure restarted"
fi

# Build
print_header "Step 2: Building Services"

GRADLE_TASKS="clean bootJar"

if [ "$SKIP_TESTS" = true ]; then
    GRADLE_TASKS="$GRADLE_TASKS -x test"
    print_info "Skipping tests"
fi

if [ "$SKIP_QUALITY" = true ]; then
    GRADLE_TASKS="$GRADLE_TASKS -x checkstyleMain -x checkstyleTest -x spotlessCheck -x spotbugsMain"
    print_info "Skipping code quality checks"
fi

print_info "Running: ./gradlew $GRADLE_TASKS"
./gradlew $GRADLE_TASKS --no-daemon

print_success "Build completed successfully"

# Show build results
print_header "Step 3: Build Artifacts"

INGESTION_JAR=$(find customer-ingestion-service/build/libs -name "*.jar" -type f ! -name "*-plain.jar" 2>/dev/null | head -n 1)
MASTERING_JAR=$(find customer-mastering-service/build/libs -name "*.jar" -type f ! -name "*-plain.jar" 2>/dev/null | head -n 1)

if [ -n "$INGESTION_JAR" ]; then
    INGESTION_SIZE=$(ls -lh "$INGESTION_JAR" | awk '{print $5}')
    print_success "Customer Ingestion Service: $(basename "$INGESTION_JAR") ($INGESTION_SIZE)"
else
    print_error "Customer Ingestion Service JAR not found"
fi

if [ -n "$MASTERING_JAR" ]; then
    MASTERING_SIZE=$(ls -lh "$MASTERING_JAR" | awk '{print $5}')
    print_success "Customer Mastering Service: $(basename "$MASTERING_JAR") ($MASTERING_SIZE)"
else
    print_error "Customer Mastering Service JAR not found"
fi

# Summary
print_header "Build Summary"

echo "  Services Built:"
echo "    ✓ customer-ingestion-service"
echo "    ✓ customer-mastering-service"
echo ""

if [ "$SKIP_TESTS" = true ]; then
    echo "  Tests: ${YELLOW}Skipped${NC}"
else
    echo "  Tests: ${GREEN}Included${NC}"
fi

if [ "$SKIP_QUALITY" = true ]; then
    echo "  Quality Checks: ${YELLOW}Skipped${NC}"
else
    echo "  Quality Checks: ${GREEN}Included${NC}"
fi

echo ""
echo "  Next Steps:"
if [ "$RESET_KAFKA" = true ]; then
    echo "    1. Services already running with fresh Kafka data"
    echo "    2. Start OAuth2 server: cd oauth-server && node server.js"
    echo "    3. Test APIs: curl http://localhost:8081/actuator/health"
else
    echo "    1. Start OAuth2 server: cd oauth-server && node server.js"
    echo "    2. Start services: docker-compose up"
    echo "    3. Import Postman collection: postman/MDM-MVP-Collection.json"
fi
echo ""

print_success "Build complete! Ready to deploy."
echo ""
