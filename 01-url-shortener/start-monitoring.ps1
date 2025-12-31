# 🚀 Quick Start Scripts for Monitoring

## start-monitoring.ps1
# Start Prometheus and Grafana services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Monitoring Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Start Prometheus
$prometheusPath = "C:\Softwares\prometheus"
if (Test-Path $prometheusPath) {
    Write-Host "Starting Prometheus..." -ForegroundColor Yellow
    Start-Process powershell -ArgumentList "cd $prometheusPath; .\prometheus.exe --config.file=prometheus.yml" -WindowStyle Normal
    Write-Host "✓ Prometheus starting on http://localhost:9090" -ForegroundColor Green
} else {
    Write-Host "✗ Prometheus not found at $prometheusPath" -ForegroundColor Red
    Write-Host "  Please update the path or install Prometheus" -ForegroundColor Yellow
}

# Wait a moment
Start-Sleep -Seconds 3

# Start Grafana (as Windows Service)
Write-Host ""
Write-Host "Starting Grafana..." -ForegroundColor Yellow
try {
    net start Grafana
    Write-Host "✓ Grafana starting on http://localhost:3000" -ForegroundColor Green
} catch {
    Write-Host "✗ Grafana service not found or already running" -ForegroundColor Yellow
    Write-Host "  If installed manually, run: grafana-server.exe from Grafana bin directory" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Monitoring Stack Status" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Waiting 10 seconds for services to start..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check if services are accessible
Write-Host ""
Write-Host "Service Status:" -ForegroundColor White
Write-Host ""

# Check Prometheus
try {
    $response = Invoke-WebRequest -Uri "http://localhost:9090/-/healthy" -TimeoutSec 2 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Prometheus:  RUNNING - http://localhost:9090" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Prometheus:  NOT ACCESSIBLE" -ForegroundColor Red
}

# Check Grafana
try {
    $response = Invoke-WebRequest -Uri "http://localhost:3000/api/health" -TimeoutSec 2 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Grafana:     RUNNING - http://localhost:3000 (admin/admin)" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Grafana:     NOT ACCESSIBLE" -ForegroundColor Red
}

# Check Application
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/prometheus" -TimeoutSec 2 -UseBasicParsing
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Application: RUNNING - http://localhost:8080/actuator/prometheus" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Application: NOT RUNNING - Start your Spring Boot app!" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Next Steps" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1. Open Prometheus: http://localhost:9090" -ForegroundColor White
Write-Host "   - Check targets: http://localhost:9090/targets" -ForegroundColor White
Write-Host ""
Write-Host "2. Open Grafana: http://localhost:3000" -ForegroundColor White
Write-Host "   - Login: admin / admin (change password on first login)" -ForegroundColor White
Write-Host "   - Add Prometheus data source: http://localhost:9090" -ForegroundColor White
Write-Host ""
Write-Host "3. Import dashboard from: PROMETHEUS-GRAFANA-GUIDE.md" -ForegroundColor White
Write-Host ""
Write-Host "See PROMETHEUS-GRAFANA-GUIDE.md for detailed setup instructions" -ForegroundColor Cyan
