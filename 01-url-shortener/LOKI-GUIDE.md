# 📝 Loki Setup Guide (Optional)

## What is Loki?

**Loki** is Grafana's log aggregation system - think of it as "Prometheus, but for logs."

### Current Setup (What You Have)

- ✅ **Prometheus** - Collects metrics (numbers, counters, gauges)
- ✅ **Grafana** - Visualizes metrics in dashboards

### With Loki (Optional)

- ✅ **Prometheus** - Metrics
- ✅ **Loki** - Logs (application logs, error messages, debug info)
- ✅ **Grafana** - Visualizes both metrics AND logs together

---

## 🤔 Do You Need Loki?

### ✅ You DON'T need Loki if:

- You're just learning monitoring basics
- You're happy viewing logs in console/files
- You only want metrics (numbers, charts)
- You want to keep setup simple

### ✅ You SHOULD install Loki if:

- You want to search logs in Grafana UI
- You want to correlate metrics with logs (click a spike → see logs)
- You have multiple servers and want centralized logging
- You're running production systems

---

## 📊 What You Get With Loki

### Before Loki:

```
Grafana Dashboard shows spike in errors at 2:30 PM
↓
You manually check terminal/log files
↓
Search for timestamps around 2:30 PM
```

### After Loki:

```
Grafana Dashboard shows spike in errors at 2:30 PM
↓
Click on the spike in graph
↓
Instantly see all error logs from that exact time
```

---

## 🚀 Quick Install Guide (Windows)

### Step 1: Download Loki

```powershell
# Create directory
mkdir C:\Softwares\loki
cd C:\Softwares\loki

# Download Loki for Windows (latest version)
# Go to: https://github.com/grafana/loki/releases
# Download: loki-windows-amd64.exe.zip
# Extract to C:\Softwares\loki\
```

### Step 2: Create Loki Configuration

Save as `loki-config.yaml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  path_prefix: C:\Softwares\loki\data
  storage:
    filesystem:
      chunks_directory: C:\Softwares\loki\data\chunks
      rules_directory: C:\Softwares\loki\data\rules
  replication_factor: 1
  ring:
    instance_addr: 127.0.0.1
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: boltdb-shipper
      object_store: filesystem
      schema: v11
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://localhost:9093

# Limits
limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  max_cache_freshness_per_query: 10m
  split_queries_by_interval: 15m
```

### Step 3: Download Promtail (Log Shipper)

Promtail collects logs from your application and sends them to Loki.

```powershell
# Download Promtail for Windows
# Same release page: https://github.com/grafana/loki/releases
# Download: promtail-windows-amd64.exe.zip
# Extract to C:\Softwares\loki\
```

### Step 4: Configure Promtail

Save as `promtail-config.yaml`:

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: C:\Softwares\loki\positions.yaml

clients:
  - url: http://localhost:3100/loki/api/v1/push

scrape_configs:
  # Collect logs from your URL Shortener application
  - job_name: url-shortener
    static_configs:
      - targets:
          - localhost
        labels:
          job: url-shortener
          __path__: C:\Users\HP\Desktop\System Design\01-url-shortener\logs\*.log
```

### Step 5: Update Spring Boot Logging

Ensure your application writes logs to files:

```yaml
# application.yml
logging:
  file:
    name: logs/url-shortener.log
    max-size: 10MB
    max-history: 30
  pattern:
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### Step 6: Start Services

```powershell
# Terminal 1: Start Loki
cd C:\Softwares\loki
.\loki-windows-amd64.exe -config.file=loki-config.yaml

# Terminal 2: Start Promtail
cd C:\Softwares\loki
.\promtail-windows-amd64.exe -config.file=promtail-config.yaml
```

### Step 7: Add Loki to Grafana

1. Open Grafana: http://localhost:3000
2. Click ⚙️ → **Data Sources** → **Add data source**
3. Select **Loki**
4. Configure:
   - **Name:** `Loki`
   - **URL:** `http://localhost:3100`
5. Click **Save & Test**

---

## 📊 Using Loki in Grafana

### View Logs

1. Click **Explore** (compass icon)
2. Select **Loki** as data source
3. Query logs:
   ```logql
   {job="url-shortener"}
   ```

### Filter Logs

```logql
# Only ERROR logs
{job="url-shortener"} |= "ERROR"

# Only cache-related logs
{job="url-shortener"} |= "CACHE"

# Exclude health checks
{job="url-shortener"} != "health"

# Last 5 minutes
{job="url-shortener"} [5m]
```

### Add Logs Panel to Dashboard

1. Edit your dashboard
2. Add new panel
3. Select **Loki** as data source
4. Query: `{job="url-shortener"} |= "ERROR"`
5. Visualization: **Logs**

---

## 🎯 Automation Script

Save as `start-loki.ps1`:

```powershell
Write-Host "Starting Loki and Promtail..." -ForegroundColor Cyan

# Start Loki
$lokiPath = "C:\Softwares\loki"
Start-Process powershell -ArgumentList "cd $lokiPath; .\loki-windows-amd64.exe -config.file=loki-config.yaml" -WindowStyle Normal

Start-Sleep -Seconds 5

# Start Promtail
Start-Process powershell -ArgumentList "cd $lokiPath; .\promtail-windows-amd64.exe -config.file=promtail-config.yaml" -WindowStyle Normal

Write-Host "✓ Loki started on http://localhost:3100" -ForegroundColor Green
Write-Host "✓ Promtail started on http://localhost:9080" -ForegroundColor Green
```

---

## 🔍 Useful LogQL Queries

```logql
# All logs from URL shortener
{job="url-shortener"}

# Only cache hits
{job="url-shortener"} |= "CACHE HIT"

# Only database queries
{job="url-shortener"} |= "DATABASE"

# Error count per minute
rate({job="url-shortener"} |= "ERROR" [1m])

# Parse JSON logs (if using JSON format)
{job="url-shortener"} | json | level="ERROR"

# Logs from specific class
{job="url-shortener"} |= "UrlService"

# Performance - slow operations
{job="url-shortener"} |= "SLOW OPERATION"
```

---

## 📈 Full Monitoring Stack

With Loki installed, you have:

```
┌─────────────────────────────────────────┐
│         Your Application                │
│   - Metrics → Prometheus                │
│   - Logs → Loki (via Promtail)          │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│              Grafana                    │
│   - Metrics Dashboard (Prometheus)      │
│   - Logs Explorer (Loki)                │
│   - Unified View (Metrics + Logs)       │
└─────────────────────────────────────────┘
```

---

## 🎨 Dashboard with Logs

### Create Combined Panel

1. **Top panel:** Error rate metric

   ```promql
   rate(http_server_requests_seconds_count{status=~"5.."}[5m])
   ```

2. **Bottom panel:** Error logs

   ```logql
   {job="url-shortener"} |= "ERROR"
   ```

3. **Link them:** Click error spike → see actual error messages

---

## 🛠️ Troubleshooting

### Loki not receiving logs

1. **Check Promtail status:**

   ```powershell
   curl http://localhost:9080/targets
   ```

2. **Verify log file path exists:**

   ```powershell
   Test-Path "C:\Users\HP\Desktop\System Design\01-url-shortener\logs\url-shortener.log"
   ```

3. **Check Promtail logs** in its terminal window

### No logs in Grafana

1. **Verify time range** (last 15 minutes)
2. **Check Loki endpoint:**
   ```powershell
   curl http://localhost:3100/ready
   ```
3. **Test query in Explore** before adding to dashboard

---

## 📝 Recommendation

### For Learning: **SKIP Loki for now**

- Focus on Prometheus + Grafana first
- View logs in your terminal/console
- Come back to Loki when comfortable with metrics

### For Production: **Install Loki**

- Essential for debugging production issues
- Correlate metrics with actual error messages
- Centralized logging across multiple servers

---

## 🎯 Complete Services Overview

| Service    | Port | Purpose          | Required?   |
| ---------- | ---- | ---------------- | ----------- |
| App        | 8080 | Your application | ✅ Yes      |
| Prometheus | 9090 | Metrics storage  | ✅ Yes      |
| Grafana    | 3000 | Visualization    | ✅ Yes      |
| Loki       | 3100 | Log storage      | ⬜ Optional |
| Promtail   | 9080 | Log collection   | ⬜ Optional |

---

**For now, you can safely ignore the Loki message in Grafana. Your monitoring setup is complete without it!** ✅
