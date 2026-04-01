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
echo -e "${BLUE}  MDM MVP - Stop Services${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

# Stop Docker Compose
stop_docker_compose() {
    print_status "Stopping Docker Compose services..."
    
    cd "$PROJECT_DIR"
    
    if [ -f "docker-compose.yml" ]; then
        if docker compose version &> /dev/null; then
            docker compose down
        else
            docker-compose down
        fi
        print_success "Docker Compose services stopped"
    else
        print_status "docker-compose.yml not found, skipping..."
    fi
}

# Stop Kubernetes
stop_kubernetes() {
    print_status "Stopping Kubernetes deployment..."
    
    if command -v kubectl &> /dev/null; then
        if kubectl get namespace mdm-system &> /dev/null; then
            kubectl delete namespace mdm-system
            print_success "Kubernetes namespace deleted"
        else
            print_status "mdm-system namespace not found, skipping..."
        fi
    else
        print_status "kubectl not found, skipping..."
    fi
}

# Stop local Java processes
stop_local() {
    print_status "Stopping local Java processes..."
    
    cd "$PROJECT_DIR"
    
    # Stop using PID files
    if [ -d ".pids" ]; then
        if [ -f ".pids/ingestion.pid" ]; then
            kill $(cat .pids/ingestion.pid) 2>/dev/null && \
                print_success "Ingestion service stopped (PID: $(cat .pids/ingestion.pid))" || \
                print_status "Ingestion service not running"
            rm -f .pids/ingestion.pid
        fi
        
        if [ -f ".pids/mastering.pid" ]; then
            kill $(cat .pids/mastering.pid) 2>/dev/null && \
                print_success "Mastering service stopped (PID: $(cat .pids/mastering.pid))" || \
                print_status "Mastering service not running"
            rm -f .pids/mastering.pid
        fi
        
        rmdir .pids 2>/dev/null || true
    fi
    
    # Force kill by port
    print_status "Cleaning up ports 8080 and 8081..."
    
    if command -v lsof &> /dev/null; then
        lsof -ti:8080 | xargs kill -9 2>/dev/null && \
            print_success "Process on port 8080 killed" || \
            print_status "No process on port 8080"
        
        lsof -ti:8081 | xargs kill -9 2>/dev/null && \
            print_success "Process on port 8081 killed" || \
            print_status "No process on port 8081"
    elif command -v fuser &> /dev/null; then
        fuser -k 8080/tcp 2>/dev/null && \
            print_success "Process on port 8080 killed" || \
            print_status "No process on port 8080"
        
        fuser -k 8081/tcp 2>/dev/null && \
            print_success "Process on port 8081 killed" || \
            print_status "No process on port 8081"
    fi
}

# Stop Docker containers by name
stop_docker_containers() {
    print_status "Stopping MDM Docker containers..."
    
    docker stop mdm-mvp-customer-ingestion-service-1 2>/dev/null && \
        print_success "Ingestion container stopped" || \
        print_status "Ingestion container not running"
    
    docker stop mdm-mvp-customer-mastering-service-1 2>/dev/null && \
        print_success "Mastering container stopped" || \
        print_status "Mastering container not running"
    
    docker stop mdm-mvp-kafka-1 2>/dev/null && \
        print_success "Kafka container stopped" || \
        print_status "Kafka container not running"
    
    docker stop mdm-mvp-postgres-1 2>/dev/null && \
        print_success "Postgres container stopped" || \
        print_status "Postgres container not running"
}

# Main
main() {
    echo -e "${YELLOW}This will stop all MDM MVP services.${NC}"
    read -p "Continue? [y/N]: " confirm
    
    if [[ ! $confirm =~ ^[Yy]$ ]]; then
        print_status "Cancelled"
        exit 0
    fi
    
    echo ""
    
    stop_docker_compose
    echo ""
    
    stop_docker_containers
    echo ""
    
    stop_kubernetes
    echo ""
    
    stop_local
    echo ""
    
    print_success "All cleanup complete!"
}

main
