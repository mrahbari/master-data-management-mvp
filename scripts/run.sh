#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  MDM MVP - Complete Startup Script${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Function to print status
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."
    
    # Check Java
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -ge 17 ]; then
            print_success "Java 17+ found: $(java -version 2>&1 | head -n 1)"
        else
            print_error "Java 17+ required, found: Java $JAVA_VERSION"
            exit 1
        fi
    else
        print_error "Java not found. Please install Java 17+"
        exit 1
    fi
    
    # Check Docker
    if command -v docker &> /dev/null; then
        print_success "Docker found: $(docker --version)"
    else
        print_warning "Docker not found. Skipping Docker checks."
    fi
    
    # Check Docker Compose
    if command -v docker-compose &> /dev/null || docker compose version &> /dev/null; then
        print_success "Docker Compose found"
    else
        print_warning "Docker Compose not found. Skipping Docker Compose checks."
    fi
    
    # Check kubectl
    if command -v kubectl &> /dev/null; then
        print_success "kubectl found: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"
    else
        print_warning "kubectl not found. Skipping Kubernetes checks."
    fi
    
    echo ""
}

# Show menu
show_menu() {
    echo -e "${YELLOW}Select run mode:${NC}"
    echo "  1) Docker Compose (Recommended for testing)"
    echo "  2) Kubernetes (Full deployment)"
    echo "  3) Local Native (Java directly, needs external Kafka+Postgres)"
    echo "  4) Build Only (Compile without running)"
    echo "  5) Stop All (Clean up everything)"
    echo "  0) Exit"
    echo ""
    read -p "Enter choice [0-5]: " choice
}

# Run with Docker Compose
run_docker_compose() {
    print_status "Starting MDM MVP with Docker Compose..."
    echo ""

    cd "$PROJECT_DIR"

    # Check if docker-compose.yml exists
    if [ ! -f "docker-compose.yml" ]; then
        print_error "docker-compose.yml not found!"
        exit 1
    fi

    # Try docker compose (v2) first, fall back to docker-compose (v1)
    local compose_cmd=""
    if docker compose version &> /dev/null; then
        compose_cmd="docker compose"
    else
        compose_cmd="docker-compose"
    fi

    # Attempt to start services. If network mismatch error occurs, recreate.
    OUTPUT=$($compose_cmd up --build -d 2>&1)
    EXIT_CODE=$?

    if [ $EXIT_CODE -ne 0 ] && echo "$OUTPUT" | grep -q "needs to be recreated"; then
        print_warning "Docker network configuration changed. Recreating network..."
        $compose_cmd down --remove-orphans
        print_status "Starting services with fresh network..."
        $compose_cmd up --build -d
    elif [ $EXIT_CODE -ne 0 ]; then
        print_error "Failed to start services:"
        echo "$OUTPUT"
        exit 1
    fi

    echo ""
    print_success "Services starting in background..."
    echo ""
    print_status "Waiting for services to be healthy (this may take 1-2 minutes)..."

    # Wait for services
    sleep 10

    # Check health
    check_docker_health

    echo ""
    print_status "View logs with: docker-compose logs -f"
    print_status "Stop services with: ./scripts/stop.sh or docker-compose down"
}

# Check Docker service health
check_docker_health() {
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        echo -ne "Health check attempt $attempt/$max_attempts\r"
        
        # Check if all services are healthy
        healthy_count=$(docker-compose ps 2>/dev/null | grep -c "(healthy)" || true)
        
        if [ "$healthy_count" -ge 4 ]; then
            echo ""
            print_success "All services are healthy!"
            echo ""
            docker-compose ps
            return 0
        fi
        
        sleep 5
        attempt=$((attempt + 1))
    done
    
    echo ""
    print_warning "Some services may still be starting. Check status with: docker-compose ps"
}

# Run with Kubernetes
run_kubernetes() {
    print_status "Starting MDM MVP on Kubernetes..."
    echo ""
    
    cd "$PROJECT_DIR"
    
    # Check kubectl
    if ! command -v kubectl &> /dev/null; then
        print_error "kubectl not found. Please install kubectl."
        exit 1
    fi
    
    # Check cluster connection
    if ! kubectl cluster-info &> /dev/null; then
        print_error "Cannot connect to Kubernetes cluster. Please configure kubectl."
        exit 1
    fi
    
    print_success "Connected to Kubernetes cluster"
    echo ""
    
    print_status "Creating namespace..."
    kubectl apply -f k8s/namespace.yaml
    
    print_status "Applying ConfigMap and Secrets..."
    kubectl apply -f k8s/configmap.yaml
    kubectl apply -f k8s/secrets.yaml
    
    print_status "Deploying Kafka (this may take a minute)..."
    kubectl apply -f k8s/kafka-deployment.yaml
    
    print_status "Deploying PostgreSQL..."
    kubectl apply -f k8s/postgres-deployment.yaml
    
    print_status "Waiting for infrastructure to be ready..."
    kubectl wait --for=condition=ready pod -l app=kafka -n mdm-system --timeout=120s || print_warning "Kafka not ready yet"
    kubectl wait --for=condition=ready pod -l app=postgres -n mdm-system --timeout=60s || print_warning "Postgres not ready yet"
    
    print_status "Deploying Customer Ingestion Service..."
    kubectl apply -f k8s/customer-ingestion-deployment.yaml
    
    print_status "Deploying Customer Mastering Service..."
    kubectl apply -f k8s/customer-mastering-deployment.yaml
    
    echo ""
    print_success "Deployment initiated!"
    echo ""
    print_status "Check deployment status:"
    echo "  kubectl get pods -n mdm-system"
    echo "  kubectl get services -n mdm-system"
    echo ""
    print_status "Port-forward for local access:"
    echo "  kubectl port-forward svc/customer-ingestion-service 8080:80 -n mdm-system &"
    echo "  kubectl port-forward svc/customer-mastering-service 8081:80 -n mdm-system &"
    echo ""
    print_status "View logs:"
    echo "  kubectl logs -f deployment/customer-ingestion-service -n mdm-system"
    echo "  kubectl logs -f deployment/customer-mastering-service -n mdm-system"
    echo ""
    print_status "Cleanup:"
    echo "  kubectl delete namespace mdm-system"
}

# Run locally (native Java)
run_local() {
    print_status "Starting MDM MVP locally (native Java)..."
    echo ""
    
    cd "$PROJECT_DIR"
    
    # Check for external dependencies
    print_status "Checking external dependencies..."
    
    # Check Kafka
    if ! nc -z localhost 9092 2>/dev/null; then
        print_error "Kafka not running on localhost:9092"
        print_status "Start Kafka with: docker-compose up kafka"
        exit 1
    fi
    print_success "Kafka found on localhost:9092"
    
    # Check PostgreSQL
    if ! nc -z localhost 5432 2>/dev/null; then
        print_error "PostgreSQL not running on localhost:5432"
        print_status "Start PostgreSQL with: docker-compose up postgres"
        exit 1
    fi
    print_success "PostgreSQL found on localhost:5432"
    
    echo ""
    print_status "Building services..."
    
    # Build Ingestion Service
    print_status "Building Customer Ingestion Service..."
    cd "$PROJECT_DIR/customer-ingestion-service"
    ./gradlew clean build -x test || {
        print_error "Failed to build Ingestion Service"
        exit 1
    }
    print_success "Ingestion Service built"
    
    # Build Mastering Service
    print_status "Building Customer Mastering Service..."
    cd "$PROJECT_DIR/customer-mastering-service"
    ./gradlew clean build -x test || {
        print_error "Failed to build Mastering Service"
        exit 1
    }
    print_success "Mastering Service built"
    
    echo ""
    print_status "Starting services..."
    
    # Start Ingestion Service
    print_status "Starting Customer Ingestion Service on port 8080..."
    cd "$PROJECT_DIR/customer-ingestion-service"
    java -jar build/libs/*.jar \
        --server.port=8080 \
        --spring.kafka.bootstrap-servers=localhost:9092 \
        &
    INGESTION_PID=$!
    
    # Wait a bit
    sleep 5
    
    # Start Mastering Service
    print_status "Starting Customer Mastering Service on port 8081..."
    cd "$PROJECT_DIR/customer-mastering-service"
    java -jar build/libs/*.jar \
        --server.port=8081 \
        --spring.kafka.bootstrap-servers=localhost:9092 \
        --spring.datasource.url=jdbc:postgresql://localhost:5432/mdm_db \
        --spring.datasource.username=mdm_user \
        --spring.datasource.password=mdm_password \
        &
    MASTERING_PID=$!
    
    echo ""
    print_success "Services started!"
    echo ""
    print_status "Ingestion Service PID: $INGESTION_PID"
    print_status "Mastering Service PID: $MASTERING_PID"
    echo ""
    print_status "Stop services with:"
    echo "  kill $INGESTION_PID $MASTERING_PID"
    echo ""
    print_status "Or run: ./scripts/stop.sh"
    
    # Save PIDs
    mkdir -p "$PROJECT_DIR/.pids"
    echo $INGESTION_PID > "$PROJECT_DIR/.pids/ingestion.pid"
    echo $MASTERING_PID > "$PROJECT_DIR/.pids/mastering.pid"
}

# Build only
build_only() {
    print_status "Building MDM MVP services..."
    echo ""
    
    cd "$PROJECT_DIR"
    
    # Build Ingestion Service
    print_status "Building Customer Ingestion Service..."
    cd "$PROJECT_DIR/customer-ingestion-service"
    ./gradlew clean build || {
        print_error "Failed to build Ingestion Service"
        exit 1
    }
    print_success "Ingestion Service built: build/libs/*.jar"
    
    # Build Mastering Service
    print_status "Building Customer Mastering Service..."
    cd "$PROJECT_DIR/customer-mastering-service"
    ./gradlew clean build || {
        print_error "Failed to build Mastering Service"
        exit 1
    }
    print_success "Mastering Service built: build/libs/*.jar"
    
    echo ""
    print_success "Build complete!"
}

# Stop all services
stop_all() {
    print_status "Stopping all MDM MVP services..."
    echo ""
    
    cd "$PROJECT_DIR"
    
    # Stop Docker Compose
    if [ -f "docker-compose.yml" ]; then
        print_status "Stopping Docker Compose services..."
        if docker compose version &> /dev/null; then
            docker compose down
        else
            docker-compose down
        fi
        print_success "Docker Compose services stopped"
    fi
    
    # Stop Kubernetes
    if command -v kubectl &> /dev/null; then
        if kubectl get namespace mdm-system &> /dev/null; then
            print_status "Deleting Kubernetes namespace..."
            kubectl delete namespace mdm-system
            print_success "Kubernetes services stopped"
        fi
    fi
    
    # Stop local processes
    if [ -d ".pids" ]; then
        print_status "Stopping local Java processes..."
        if [ -f ".pids/ingestion.pid" ]; then
            kill $(cat .pids/ingestion.pid) 2>/dev/null || true
            rm .pids/ingestion.pid
        fi
        if [ -f ".pids/mastering.pid" ]; then
            kill $(cat .pids/mastering.pid) 2>/dev/null || true
            rm .pids/mastering.pid
        fi
        print_success "Local Java processes stopped"
    fi
    
    # Kill any remaining Java processes on our ports
    print_status "Cleaning up ports 8080 and 8081..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    lsof -ti:8081 | xargs kill -9 2>/dev/null || true
    
    echo ""
    print_success "All services stopped!"
}

# Main execution
main() {
    check_prerequisites
    show_menu
    
    case $choice in
        1)
            run_docker_compose
            ;;
        2)
            run_kubernetes
            ;;
        3)
            run_local
            ;;
        4)
            build_only
            ;;
        5)
            stop_all
            ;;
        0)
            print_status "Exiting..."
            exit 0
            ;;
        *)
            print_error "Invalid choice"
            exit 1
            ;;
    esac
}

main
