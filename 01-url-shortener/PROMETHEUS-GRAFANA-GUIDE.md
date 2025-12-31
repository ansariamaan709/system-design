# 📊 Prometheus & Grafana Setup Guide (Windows)

**Complete guide for monitoring your URL Shortener application**

---

## 📋 Table of Contents

1. [What is Prometheus & Grafana?](#what-is-prometheus--grafana)
2. [Installation Steps](#installation-steps)
3. [Configuration](#configuration)
4. [Creating Dashboards](#creating-dashboards)
5. [Key Metrics to Monitor](#key-metrics-to-monitor)
6. [Troubleshooting](#troubleshooting)

---

## 🎯 What is Prometheus & Grafana?

### Prometheus

- **Time-series database** that stores metrics
- **Scrapes metrics** from your application every 15 seconds
- **Queries data** using PromQL (Prometheus Query Language)
- **Alerting** capabilities

### Grafana

- **Visualization tool** - beautiful dashboards
- **Connects to Prometheus** as a data source
- **Real-time monitoring** with auto-refresh
- **Alerting & notifications**

### How They Work Together

```
Your App (Port 8080)
    ↓ /actuator/prometheus
Prometheus (Port 9090) ← Scrapes metrics every 15s
    ↓ Stores time-series data
Grafana (Port 3000) ← Queries Prometheus
    ↓ Displays beautiful charts
Your Browser ← View dashboards
```

---

## 🚀 Installation Steps

### Step 1: Install Grafana

#### Download Grafana

1. Go to: https://grafana.com/grafana/download?platform=windows
2. Download the latest **OSS version** (Open Source Software)
3. Choose **Windows Installer (.msi)**

#### Install Grafana

```powershell
# Run the downloaded .msi file
# Or using Windows Package Manager
winget install Grafana.Grafana
```

#### Start Grafana Service

```powershell
# Option 1: Start as Windows Service (automatically starts on boot)
net start Grafana

# Option 2: Run manually (if installed to custom location)
cd "C:\Program Files\GrafanaLabs\grafana\bin"
.\grafana-server.exe
```

**Verify Grafana is running:**

```powershell
# Open browser and go to:
http://localhost:3000

# Default credentials:
# Username: admin
# Password: admin
# (You'll be prompted to change it on first login)
```

---

## ⚙️ Configuration

### Step 2: Configure Prometheus

#### Create Prometheus Configuration File

Create a file: `prometheus.yml` in your Prometheus directory (e.g., `C:\Softwares\prometheus\`)

```yaml
# prometheus.yml
global:
  scrape_interval: 15s # Scrape metrics every 15 seconds
  evaluation_interval: 15s # Evaluate rules every 15 seconds

# Scrape configurations
scrape_configs:
  # Monitor Prometheus itself
  - job_name: "prometheus"
    static_configs:
      - targets: ["localhost:9090"]

  # Monitor your URL Shortener application
  - job_name: "url-shortener"
    metrics_path: "/actuator/prometheus"
    scrape_interval: 10s
    static_configs:
      - targets: ["localhost:8080"]
        labels:
          application: "url-shortener"
          environment: "local"
```

#### Start Prometheus

```powershell
# Navigate to Prometheus directory
cd C:\Softwares\prometheus

# Start Prometheus
.\prometheus.exe --config.file=prometheus.yml

# Keep this terminal open
```

**Verify Prometheus is running:**

```powershell
# Open browser and go to:
http://localhost:9090

# Check targets (should see url-shortener)
http://localhost:9090/targets
```

---

### Step 3: Connect Grafana to Prometheus

#### 1. Login to Grafana

```
http://localhost:3000
Username: admin
Password: admin (change on first login)
```

#### 2. Add Prometheus Data Source

**Click:** `⚙️ Configuration` → `Data Sources` → `Add data source`

**Select:** Prometheus

**Configure:**

- **Name:** `Prometheus`
- **URL:** `http://localhost:9090`
- **Scrape interval:** `15s`
- Click **"Save & Test"**

You should see: ✅ **"Data source is working"**

---

## 📊 Creating Dashboards

### Quick Start: Import Pre-Built Dashboard

#### Step 1: Create Dashboard JSON

Save this as `url-shortener-dashboard.json`:

```json
{
  "title": "URL Shortener Metrics",
  "uid": "url-shortener-1",
  "timezone": "browser",
  "panels": [
    {
      "title": "Total URLs Created",
      "targets": [
        {
          "expr": "urls_created_total",
          "refId": "A"
        }
      ],
      "type": "stat",
      "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 }
    },
    {
      "title": "URL Creation Rate (per minute)",
      "targets": [
        {
          "expr": "rate(urls_created_total[5m]) * 60",
          "refId": "A"
        }
      ],
      "type": "graph",
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 4 }
    },
    {
      "title": "Cache Hit Rate",
      "targets": [
        {
          "expr": "rate(url_shortener_cache_hits_total[5m]) / (rate(url_shortener_cache_hits_total[5m]) + rate(url_shortener_cache_misses_total[5m])) * 100",
          "refId": "A",
          "legendFormat": "Cache Hit %"
        }
      ],
      "type": "graph",
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 4 }
    },
    {
      "title": "Redirect Response Time (p99)",
      "targets": [
        {
          "expr": "histogram_quantile(0.99, rate(controller_method_execution_seconds_bucket{method=\"redirect\"}[5m]))",
          "refId": "A"
        }
      ],
      "type": "graph",
      "gridPos": { "h": 8, "w": 12, "x": 0, "y": 12 }
    },
    {
      "title": "JVM Memory Usage",
      "targets": [
        {
          "expr": "jvm_memory_used_bytes{area=\"heap\"}",
          "refId": "A",
          "legendFormat": "Heap Used"
        },
        {
          "expr": "jvm_memory_max_bytes{area=\"heap\"}",
          "refId": "B",
          "legendFormat": "Heap Max"
        }
      ],
      "type": "graph",
      "gridPos": { "h": 8, "w": 12, "x": 12, "y": 12 }
    }
  ],
  "refresh": "10s",
  "schemaVersion": 27,
  "version": 1
}
```

#### Step 2: Import Dashboard

1. In Grafana, click **`+`** → **"Import"**
2. Click **"Upload JSON file"** → Select your `url-shortener-dashboard.json`
3. Select **Prometheus** as data source
4. Click **"Import"**

---

### Manual Dashboard Creation

#### Create Your First Panel

1. **Click:** `+` → `Dashboard` → `Add new panel`

2. **Configure Query:**

   ```promql
   # Total URLs created
   urls_created_total
   ```

3. **Panel Settings:**

   - **Title:** "Total URLs Created"
   - **Type:** Stat
   - **Unit:** Number
   - Click **"Apply"**

4. **Save Dashboard:** Click 💾 Save Dashboard → Name it "URL Shortener"

---

## 📈 Key Metrics to Monitor

### 1. Application Metrics

#### URLs Created (Counter)

```promql
# Total count
urls_created_total

# Rate per minute
rate(urls_created_total[5m]) * 60
```

#### URLs Resolved (Redirects)

```promql
# Total redirects
urls_resolved_total

# Rate per second
rate(urls_resolved_total[1m])
```

#### Cache Performance

```promql
# Cache hit rate (%)
rate(url_shortener_cache_hits_total[5m]) /
(rate(url_shortener_cache_hits_total[5m]) + rate(url_shortener_cache_misses_total[5m])) * 100

# Cache misses
rate(url_shortener_cache_misses_total[5m])
```

### 2. Performance Metrics

#### Response Time (p50, p95, p99)

```promql
# p99 latency for redirect endpoint
histogram_quantile(0.99,
  rate(controller_method_execution_seconds_bucket{method="redirect"}[5m])
)

# p95 latency
histogram_quantile(0.95,
  rate(controller_method_execution_seconds_bucket{method="redirect"}[5m])
)
```

### 3. JVM Metrics

#### Memory Usage

```promql
# Heap memory used
jvm_memory_used_bytes{area="heap"}

# Heap memory max
jvm_memory_max_bytes{area="heap"}

# Memory usage percentage
(jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) * 100
```

#### Garbage Collection

```promql
# GC count
rate(jvm_gc_pause_seconds_count[5m])

# GC time
rate(jvm_gc_pause_seconds_sum[5m])
```

#### Threads

```promql
# Active threads
jvm_threads_live_threads

# Thread states
jvm_threads_states_threads
```

### 4. Database Connection Pool

```promql
# Active connections
hikaricp_connections_active{pool="UrlShortenerHikariPool"}

# Idle connections
hikaricp_connections_idle{pool="UrlShortenerHikariPool"}

# Connection timeout count
hikaricp_connections_timeout_total
```

### 5. HTTP Metrics

```promql
# HTTP requests by status
http_server_requests_seconds_count{status="200"}
http_server_requests_seconds_count{status="404"}

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])
```

---

## 🎨 Dashboard Examples

### Panel 1: Request Rate

```promql
# Query
rate(http_server_requests_seconds_count[5m]) * 60

# Visualization: Graph
# Legend: {{method}} {{uri}}
# Unit: req/min
```

### Panel 2: Error Rate

```promql
# Query
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Visualization: Graph (Red line)
# Alert: > 10 errors/min
```

### Panel 3: Cache Efficiency

```promql
# Queries
A: rate(url_shortener_cache_hits_total[5m])
B: rate(url_shortener_cache_misses_total[5m])

# Visualization: Stacked Graph
# Legend: Hits / Misses
```

---

## 🔧 Advanced Configuration

### Alerting Rules

Create `alerts.yml` in Prometheus directory:

```yaml
groups:
  - name: url_shortener_alerts
    interval: 30s
    rules:
      # Alert when cache hit rate < 50%
      - alert: LowCacheHitRate
        expr: |
          rate(url_shortener_cache_hits_total[5m]) / 
          (rate(url_shortener_cache_hits_total[5m]) + rate(url_shortener_cache_misses_total[5m])) < 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Cache hit rate is below 50%"
          description: "Current cache hit rate: {{ $value | humanizePercentage }}"

      # Alert when error rate > 1%
      - alert: HighErrorRate
        expr: |
          rate(http_server_requests_seconds_count{status=~"5.."}[5m]) /
          rate(http_server_requests_seconds_count[5m]) > 0.01
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate: {{ $value | humanizePercentage }}"
```

Update `prometheus.yml`:

```yaml
rule_files:
  - "alerts.yml"
```

---

## 🛠️ Troubleshooting

### Prometheus Not Scraping Application

**Check:** http://localhost:9090/targets

**If status is "DOWN":**

1. **Verify app is running:**

   ```powershell
   curl http://localhost:8080/actuator/prometheus
   ```

2. **Check Spring Boot actuator:**

   ```yaml
   # application.yml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,prometheus,metrics
   ```

3. **Check firewall:**
   ```powershell
   netsh advfirewall firewall add rule name="Allow Port 8080" dir=in action=allow protocol=TCP localport=8080
   ```

### Grafana Can't Connect to Prometheus

1. **Verify Prometheus URL:** http://localhost:9090
2. **Check if both are running:**
   ```powershell
   netstat -ano | findstr :9090
   netstat -ano | findstr :3000
   ```

### No Data in Grafana

1. **Check time range** (top-right corner) - Set to "Last 15 minutes"
2. **Verify metrics exist in Prometheus:**
   - Go to http://localhost:9090
   - Try query: `urls_created_total`
3. **Refresh dashboard:** Click 🔄 (top-right)

---

## 📝 Quick Reference

### Service URLs

| Service     | URL                                       | Default Credentials |
| ----------- | ----------------------------------------- | ------------------- |
| Application | http://localhost:8080                     | -                   |
| Metrics     | http://localhost:8080/actuator/prometheus | -                   |
| Prometheus  | http://localhost:9090                     | -                   |
| Grafana     | http://localhost:3000                     | admin / admin       |

### Useful PromQL Functions

```promql
rate(metric[5m])          # Per-second rate over 5 minutes
increase(metric[1h])      # Total increase over 1 hour
sum(metric)               # Sum of all series
avg(metric)               # Average of all series
max(metric)               # Maximum value
histogram_quantile(0.99, metric)  # 99th percentile
```

### Grafana Shortcuts

- `?` - Show keyboard shortcuts
- `d` + `k` - Toggle dark/light theme
- `v` - Toggle panel edit mode
- `Ctrl + S` - Save dashboard
- `Ctrl + H` - Hide/show panel controls

---

## 🎯 Next Steps

1. ✅ Install Prometheus & Grafana
2. ✅ Configure Prometheus to scrape your app
3. ✅ Add Prometheus as Grafana data source
4. ✅ Import URL Shortener dashboard
5. ✅ Create custom panels
6. ⬜ Set up alerting
7. ⬜ Create team dashboards
8. ⬜ Export dashboards for backup

---

## 📚 Learning Resources

- **Prometheus Docs:** https://prometheus.io/docs/
- **Grafana Docs:** https://grafana.com/docs/
- **PromQL Tutorial:** https://prometheus.io/docs/prometheus/latest/querying/basics/
- **Grafana Dashboards:** https://grafana.com/grafana/dashboards/

---

**Happy Monitoring! 📊🎉**
