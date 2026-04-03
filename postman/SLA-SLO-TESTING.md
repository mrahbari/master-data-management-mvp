# SLA/SLO/SLI Postman Testing Guide

## 📊 New Section Added: "07 - SLA/SLO/SLI Testing"

The Postman collection now includes **9 requests** for comprehensive SLA/SLO/SLI testing.

---

## 🎯 SLA/SLO/SLI Test Requests

### Request 1: Get SLO Status (Complete)

**Endpoint:** `GET /actuator/slo`

**Purpose:** Get complete SLO status including availability, error budget, burn rate, and severity level.

**Expected Response:**
```json
{
  "availability": "99.9500%",
  "errorBudgetRemaining": "67.50%",
  "burnRate": "1.20x",
  "burnRateSeverity": "NORMAL",
  "burnRateDescription": "Normal operations",
  "monthlyErrorBudgetMinutes": 43.2,
  "weeklyErrorBudgetMinutes": 10.08,
  "dailyErrorBudgetMinutes": 1.44,
  "maxAllowedErrorRate": "0.1000%",
  "sloMet": true,
  "status": "HEALTHY",
  "totalEventsProcessed": 15420,
  "totalErrors": 8,
  "duplicateRate": "27.35%",
  "timestamp": "2024-01-15T10:30:00Z",
  "sloTarget": "99.90%"
}
```

**Test Assertions:**
- ✅ Status is 200
- ✅ Has availability
- ✅ Has error budget
- ✅ Has burn rate
- ✅ SLO is met

---

### Request 2: Get SLO Health (Simple)

**Endpoint:** `GET /actuator/slo/health`

**Purpose:** Quick SLO health check (simplified response).

**Expected Responses:**

**Healthy:**
```json
{
  "availability": "99.95%",
  "errorBudget": "67.5%",
  "status": "HEALTHY"
}
```

**Warning:**
```json
{
  "availability": "99.85%",
  "errorBudget": "35.2%",
  "status": "WARNING"
}
```

**Critical:**
```json
{
  "availability": "99.70%",
  "errorBudget": "8.5%",
  "status": "CRITICAL"
}
```

---

### Request 3: Get Detailed Health (All Indicators)

**Endpoint:** `GET /actuator/health?show-details=always`

**Purpose:** See all 5 health indicators with detailed information.

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "queryTimeMs": 3,
        "rawTableRows": 1542,
        "goldenTableRows": 892
      }
    },
    "kafka": {
      "status": "UP",
      "details": {
        "produceTimeMs": 12,
        "bootstrapServers": "kafka:9092"
      }
    },
    "mdmProcessing": {
      "status": "UP",
      "details": {
        "lastProcessingTime": "2024-01-15T10:30:00Z",
        "totalProcessed": 1542,
        "totalDuplicates": 423,
        "duplicateRate": "27%"
      }
    },
    "livenessState": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    }
  }
}
```

**Health Indicators:**
1. **db** - Database connectivity and query latency
2. **kafka** - Kafka broker connectivity
3. **mdmProcessing** - Business process health (processing recency, duplicate rate)
4. **livenessState** - Kubernetes liveness probe
5. **readinessState** - Kubernetes readiness probe

---

### Request 4: Get Burn Rate Metrics

**Endpoint:** `GET /actuator/metrics/mdm.processing_errors_total`

**Purpose:** Get error count for burn rate calculation.

**Expected Response:**
```json
{
  "name": "mdm.processing_errors_total",
  "measurements": [
    {"statistic": "COUNT", "value": 8}
  ],
  "availableTags": [...]
}
```

---

### Request 5: Get Error Budget Calculation

**Endpoint:** `GET /actuator/metrics/mdm.events_processed_total`

**Purpose:** Get total events for error budget calculation.

**Expected Response:**
```json
{
  "name": "mdm.events_processed_total",
  "measurements": [
    {"statistic": "COUNT", "value": 15420}
  ],
  "availableTags": [...]
}
```

---

### Request 6: Get Latency SLI (Histogram)

**Endpoint:** `GET /actuator/metrics/mdm.event_processing_latency_seconds`

**Purpose:** Get latency histogram for p50/p95/p99 calculation.

**Expected Response:**
```json
{
  "name": "mdm.event_processing_latency_seconds",
  "measurements": [
    {"statistic": "COUNT", "value": 15420},
    {"statistic": "TOTAL_TIME", "value": 385.5},
    {"statistic": "MAX", "value": 0.245}
  ],
  "availableTags": [
    {"tag": "service", "values": ["customer-mastering-service"]}
  ]
}
```

**Calculate Percentiles:**
- p50: ~25ms
- p95: ~50ms
- p99: ~80ms

---

### Request 7: Get Deduplication Latency SLI

**Endpoint:** `GET /actuator/metrics/mdm.deduplication_lookup_latency_seconds`

**Purpose:** Get deduplication lookup latency (SLO: 99% < 10ms).

**Expected Response:**
```json
{
  "name": "mdm.deduplication_lookup_latency_seconds",
  "measurements": [
    {"statistic": "COUNT", "value": 15420},
    {"statistic": "TOTAL_TIME", "value": 45.2}
  ]
}
```

---

### Request 8: Calculate: Availability SLI

**Type:** Reference Prometheus Query

**Purpose:** Shows how to calculate availability SLI using Prometheus.

**Prometheus Query:**
```promql
sum(rate(mdm_events_processed_total[5m])) - sum(rate(mdm_processing_errors_total[5m])) 
/ 
sum(rate(mdm_events_processed_total[5m])) * 100
```

---

### Request 9: Verify: SLO Target (99.9%)

**Endpoint:** `GET /actuator/slo`

**Purpose:** Verify SLO target is configured correctly (99.9% availability).

**Expected Response:**
```json
{
  "sloTarget": "99.90%",
  "availability": "99.9500%",
  "sloMet": true,
  "status": "HEALTHY"
}
```

---

## 🧪 Testing Scenarios

### Scenario 1: SLO Health Check (2 min)

```
1. Get SLO Health (Simple)
   → Verify status is "HEALTHY"
   → Check error budget > 50%

2. Get SLO Status (Complete)
   → Verify burn rate < 2x (NORMAL)
   → Check sloMet is true
```

### Scenario 2: Health Indicators Check (2 min)

```
1. Get Detailed Health (All Indicators)
   → Verify all 5 indicators are UP
   → Check db query time < 100ms
   → Check kafka produce time < 200ms
   → Check mdmProcessing has recent timestamp
```

### Scenario 3: SLI Metrics Collection (3 min)

```
1. Get Latency SLI (Histogram)
   → Verify histogram data present
   → Calculate p50, p95, p99

2. Get Deduplication Latency SLI
   → Verify dedup lookup latency
   → Verify SLO: 99% < 10ms

3. Get Burn Rate Metrics
   → Get error count
   → Get total events
   → Calculate burn rate manually
```

### Scenario 4: Error Budget Analysis (3 min)

```
1. Get SLO Status (Complete)
   → Note error budget remaining
   → Note burn rate

2. Calculate: How long until budget exhausted?
   → At 1x burn rate: 30 days
   → At 5x burn rate: 6 days
   → At 10x burn rate: 3 days

3. Determine action based on budget:
   → > 50%: Normal operations
   → 25-50%: Monitor closely
   → 10-25%: Freeze non-critical deploys
   → < 10%: Deploy freeze, focus on reliability
```

---

## 📊 Burn Rate Severity Guide

| Burn Rate | Severity | Action Required |
|-----------|----------|-----------------|
| **< 2x** | NORMAL | Normal operations, deploy freely |
| **2-5x** | ELEVATED | Monitor closely, reduce deploy frequency |
| **5-10x** | HIGH | Urgent attention needed, consider deploy freeze |
| **10x+** | CRITICAL | Immediate action required, deploy freeze |

---

## 🎯 Error Budget Policy

| Budget Remaining | Status | Action |
|------------------|--------|--------|
| **> 50%** | 🟢 HEALTHY | Normal operations |
| **25-50%** | 🟡 WARNING | Monitor closely |
| **10-25%** | 🟠 ELEVATED | Freeze non-critical deploys |
| **< 10%** | 🔴 CRITICAL | Deploy freeze, focus on reliability |
| **0%** | ⚫ BREACHED | Incident review, post-mortem |

---

## ✅ Test Checklist

Before considering SLA/SLO/SLI testing complete:

- [ ] Import Postman collection
- [ ] Start both services
- [ ] Run "Get SLO Status (Complete)" - verify response
- [ ] Run "Get SLO Health (Simple)" - verify status
- [ ] Run "Get Detailed Health" - verify all 5 indicators UP
- [ ] Run "Get Latency SLI" - verify histogram data
- [ ] Run "Get Deduplication Latency SLI" - verify SLO met
- [ ] Calculate burn rate manually (verify against API)
- [ ] Verify error budget calculation
- [ ] Document current SLO status

---

**Total SLA/SLO/SLI Test Requests: 9**  
**Estimated Testing Time: 10-15 minutes**
