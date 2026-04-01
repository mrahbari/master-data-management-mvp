#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MDM MVP - Development Mode${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Cleanup function
cleanup() {
    echo ""
    print_status "Shutting down services..."
    
    if [ -n "$INGESTION_PID" ]; then
        kill $INGESTION_PID 2>/dev/null || true
        print_status "Ingestion service stopped"
    fi
    
    if [ -n "$MASTERING_PID" ]; then
        kill $MASTERING_PID 2>/dev/null || true
        print_status "Mastering service stopped"
    fi
    
    # Remove trap
    trap - EXIT INT TERM
}

# Set trap for cleanup
trap cleanup EXIT INT TERM

# Check prerequisites
print_status "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    print_error "Java not found. Please install Java 17+"
    exit 1
fi

# Check Gradle wrapper
if [ ! -f "$PROJECT_DIR/customer-ingestion-service/gradlew" ]; then
    print_error "Gradle wrapper not found"
    exit 1
fi

echo ""
print_status "Starting infrastructure with Docker Compose..."
print_status "(Kafka and PostgreSQL only)"
echo ""

cd "$PROJECT_DIR"

# Start only Kafka and PostgreSQL
docker-compose up -d kafka postgres

print_status "Waiting for Kafka to be ready..."
max_attempts=30
attempt=1

while [ $attempt -le $max_attempts ]; do
    echo -ne "Attempt $attempt/$max_attempts\r"
    
    if docker-compose exec -T kafka kafka-topics --bootstrap-server localhost:9092 --list &>/dev/null; then
        echo ""
        print_success "Kafka is ready"
        break
    fi
    
    sleep 2
    attempt=$((attempt + 1))
done

if [ $attempt -gt $max_attempts ]; then
    print_error "Kafka failed to start"
    exit 1
fi

print_status "Waiting for PostgreSQL to be ready..."

while ! docker-compose exec -T postgres pg_isready -U mdm_user -d mdm_db &>/dev/null; do
    sleep 1
done

print_success "PostgreSQL is ready"

echo ""
print_status "Initializing database..."
docker-compose exec -T postgres psql -U mdm_user -d mdm_db -c "
CREATE TABLE IF NOT EXISTS customer_raw (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    source_system VARCHAR(50) NOT NULL,
    raw_payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS customer_golden (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_email VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    confidence_score SMALLINT NOT NULL DEFAULT 100,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_source_system VARCHAR(50)
);
" 2>/dev/null || print_status "Tables may already exist"

echo ""
print_status "Building services..."

# Build Ingestion Service
print_status "Building Customer Ingestion Service..."
cd "$PROJECT_DIR/customer-ingestion-service"
./gradlew build -x test --quiet

# Build Mastering Service
print_status "Building Customer Mastering Service..."
cd "$PROJECT_DIR/customer-mastering-service"
./gradlew build -x test --quiet

print_success "Build complete"

echo ""
print_status "Starting Customer Ingestion Service on port 8080..."
cd "$PROJECT_DIR/customer-ingestion-service"

JAVA_OPTS="-Xms128m -Xmx256m"
java $JAVA_OPTS -jar build/libs/*.jar \
    --server.port=8080 \
    --spring.kafka.bootstrap-servers=localhost:9092 \
    --kafka.topics.customer-raw=customer.raw \
    2>&1 | grep -v "^\s*$" &
INGESTION_PID=$!

# Wait for ingestion service
sleep 10

# Check if ingestion service is running
if ! kill -0 $INGESTION_PID 2>/dev/null; then
    print_error "Ingestion service failed to start"
    exit 1
fi

print_success "Ingestion service started (PID: $INGESTION_PID)"

echo ""
print_status "Starting Customer Mastering Service on port 8081..."
cd "$PROJECT_DIR/customer-mastering-service"

java $JAVA_OPTS -jar build/libs/*.jar \
    --server.port=8081 \
    --spring.kafka.bootstrap-servers=localhost:9092 \
    --spring.datasource.url=jdbc:postgresql://localhost:5432/mdm_db \
    --spring.datasource.username=mdm_user \
    --spring.datasource.password=mdm_password \
    --kafka.topics.customer-raw=customer.raw \
    --kafka.topics.customer-mastered=customer.mastered \
    2>&1 | grep -v "^\s*$" &
MASTERING_PID=$!

# Wait for mastering service
sleep 10

# Check if mastering service is running
if ! kill -0 $MASTERING_PID 2>/dev/null; then
    print_error "Mastering service failed to start"
    exit 1
fi

print_success "Mastering service started (PID: $MASTERING_PID)"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Services Running!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "  Ingestion Service:  http://localhost:8080"
echo "  Mastering Service:  http://localhost:8081"
echo ""
echo "  Kafka:              localhost:9092"
echo "  PostgreSQL:         localhost:5432"
echo ""
echo -e "${YELLOW}Quick Test:${NC}"
echo '  curl -X POST http://localhost:8080/api/customers \'
echo '    -H "Content-Type: application/json" \'
echo '    -d '\''{"email": "test@example.com", "firstName": "Test", "sourceSystem": "dev"}'\'''
echo ""
echo "  Metrics:"
echo "  curl http://localhost:8080/actuator/prometheus"
echo "  curl http://localhost:8081/actuator/prometheus"
echo ""
echo -e "${YELLOW}Logs:${NC}"
echo "  Follow: docker-compose logs -f"
echo "  Clean:  docker-compose down"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# Wait for interrupt
wait
