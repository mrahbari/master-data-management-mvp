#!/bin/bash

# DLQ Management Shell Scripts
# These scripts provide CLI access to DLQ operations that complement the HTTP API endpoints

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

case "$1" in
  "list-topics")
    echo "=========================================="
    echo "Listing Kafka Topics"
    echo "=========================================="
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec kafka kafka-topics --bootstrap-server localhost:9092 --list
    ;;

  "describe-dlq")
    echo "=========================================="
    echo "Describing DLQ Topic: customer.dlq"
    echo "=========================================="
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic customer.dlq
    ;;

  "consume-dlq")
    MAX_MESSAGES=${2:-10}
    TIMEOUT_MS=${3:-10000}
    echo "=========================================="
    echo "Consuming DLQ Messages (max: $MAX_MESSAGES)"
    echo "=========================================="
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" exec kafka kafka-console-consumer \
      --bootstrap-server localhost:9092 \
      --topic customer.dlq \
      --from-beginning \
      --max-messages "$MAX_MESSAGES" \
      --timeout-ms "$TIMEOUT_MS"
    ;;

  "reprocess-manual")
    echo "=========================================="
    echo "Manual DLQ Reproduction"
    echo "=========================================="
    echo ""
    echo "Step 1: Consume DLQ messages and save to file"
    echo "  ./tests/dlq-manage.sh consume-dlq 1 > /tmp/dlq-message.json"
    echo ""
    echo "Step 2: Extract the 'originalEvent' from the DLQ message"
    echo "  cat /tmp/dlq-message.json | jq '.originalEvent'"
    echo ""
    echo "Step 3: Publish original event back to customer.raw topic"
    echo "  docker compose exec kafka kafka-console-producer \\"
    echo "    --bootstrap-server localhost:9092 \\"
    echo "    --topic customer.raw"
    echo ""
    echo "  # Then paste the originalEvent JSON and press Ctrl+D"
    echo ""
    echo "Step 4: Monitor mastering service logs"
    echo "  docker compose logs -f customer-mastering-service | grep -i 'success\\|error'"
    echo ""
    echo "Alternative: Use the HTTP API endpoint"
    echo "  curl -X POST http://localhost:8081/api/dlq/reprocess \\"
    echo "    -H 'Content-Type: application/json' \\"
    echo "    -d '@dlq-event.json'"
    echo "=========================================="
    ;;

  "logs-retry")
    TAIL_LINES=${2:-100}
    echo "=========================================="
    echo "Mastering Service Logs (Last $TAIL_LINES lines) - Retry/DLQ Events"
    echo "=========================================="
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" logs --tail="$TAIL_LINES" customer-mastering-service | grep -i -E "retry|DLQ|dead letter|send to dlq"
    ;;

  "logs-all")
    TAIL_LINES=${2:-100}
    echo "=========================================="
    echo "Mastering Service Logs (Last $TAIL_LINES lines)"
    echo "=========================================="
    docker compose -f "$PROJECT_ROOT/docker-compose.yml" logs --tail="$TAIL_LINES" customer-mastering-service
    ;;

  "stats")
    echo "=========================================="
    echo "DLQ Statistics from Prometheus Metrics"
    echo "=========================================="
    echo ""
    echo "Failed Events Total:"
    curl -s http://localhost:8081/actuator/metrics/mdm.failed_events_total | jq '.'
    echo ""
    echo "Events Processed Total:"
    curl -s http://localhost:8081/actuator/metrics/mdm.events_processed_total | jq '.'
    echo ""
    echo "DLQ Rate Calculation:"
    FAILED=$(curl -s http://localhost:8081/actuator/metrics/mdm.failed_events_total | jq '.measurements[0].value')
    PROCESSED=$(curl -s http://localhost:8081/actuator/metrics/mdm.events_processed_total | jq '.measurements[0].value')
    if [ "$PROCESSED" != "null" ] && [ "$(echo "$PROCESSED > 0" | bc)" -eq 1 ]; then
      DLQ_RATE=$(echo "scale=2; $FAILED / $PROCESSED * 100" | bc)
      echo "DLQ Rate: ${DLQ_RATE}%"
    else
      echo "DLQ Rate: N/A (no events processed yet)"
    fi
    echo "=========================================="
    ;;

  "trigger-dlq-test")
    echo "=========================================="
    echo "Triggering DLQ Test Scenario"
    echo "=========================================="
    echo ""
    echo "Sending invalid event to trigger DLQ..."
    
    # Get OAuth token
    TOKEN=$(curl -s http://localhost:9999/oauth/token \
      -H 'Content-Type: application/json' \
      -d '{"grant_type":"password","client_id":"mdm-client","client_secret":"mdm-secret","username":"admin","password":"admin123"}' \
      | python3 -c "import sys, json; print(json.load(sys.stdin)['access_token'])")

    if [ -z "$TOKEN" ]; then
      echo "❌ Failed to get OAuth token"
      exit 1
    fi

    # Send an event with invalid nationalId to trigger validation error
    echo "Sending event with invalid data to trigger DLQ..."
    curl -s -X POST http://localhost:8080/api/customers \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d '{
        "nationalId": "INVALID",
        "name": "Test User",
        "email": "test@example.com",
        "sourceSystem": "TEST"
      }' -w "\nHTTP Status: %{http_code}\n"

    echo ""
    echo "Check mastering service logs for DLQ event:"
    echo "  ./tests/dlq-manage.sh logs-retry 50"
    echo "=========================================="
    ;;

  *)
    echo "=========================================="
    echo "DLQ Management Shell Scripts"
    echo "=========================================="
    echo ""
    echo "Usage: ./tests/dlq-manage.sh <command> [options]"
    echo ""
    echo "Commands:"
    echo "  list-topics              List all Kafka topics"
    echo "  describe-dlq             Describe DLQ topic configuration"
    echo "  consume-dlq [n] [ms]     Consume n DLQ messages (default: 10, timeout: 10000ms)"
    echo "  reprocess-manual         Show manual reprocessing steps"
    echo "  logs-retry [n]           Show last n log lines filtered for retry/DLQ (default: 100)"
    echo "  logs-all [n]             Show all mastering service logs (default: 100)"
    echo "  stats                    Show DLQ statistics from Prometheus metrics"
    echo "  trigger-dlq-test         Send a test event to trigger DLQ processing"
    echo ""
    echo "HTTP API Endpoints:"
    echo "  GET  http://localhost:8081/api/dlq/stats         - Get DLQ statistics"
    echo "  GET  http://localhost:8081/api/dlq/structure     - Get DLQ message structure reference"
    echo "  GET  http://localhost:8081/api/dlq/manual-reprocess - Get manual reprocessing instructions"
    echo "  POST http://localhost:8081/api/dlq/reprocess     - Reprocess a DLQ message"
    echo ""
    echo "=========================================="
    ;;
esac
