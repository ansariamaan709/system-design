# PowerShell script to start all services with Docker Compose

Write-Host "=== Starting Kafka Clone Services ===" -ForegroundColor Cyan

# Check if Docker is running
try {
    docker info | Out-Null
    Write-Host "[OK] Docker is running" -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Build and start all services
Write-Host "`nBuilding and starting services..." -ForegroundColor Yellow
docker-compose up --build -d

# Wait for services to be healthy
Write-Host "`nWaiting for services to be healthy..." -ForegroundColor Yellow

$services = @("kafka-postgres", "kafka-redis", "kafka-broker")
foreach ($service in $services) {
    Write-Host "  Checking $service..." -ForegroundColor Gray
    $maxRetries = 60
    $retryCount = 0
    
    do {
        Start-Sleep -Seconds 2
        $health = docker inspect --format='{{.State.Health.Status}}' $service 2>$null
        $status = docker inspect --format='{{.State.Status}}' $service 2>$null
        $retryCount++
    } while ($health -ne "healthy" -and $status -ne "running" -and $retryCount -lt $maxRetries)
    
    if ($retryCount -ge $maxRetries) {
        Write-Host "  [WARN] $service may not be fully ready" -ForegroundColor Yellow
    }
    else {
        Write-Host "  [OK] $service is ready" -ForegroundColor Green
    }
}

# Display service URLs
Write-Host "`n=== Service URLs ===" -ForegroundColor Cyan
Write-Host "Kafka Broker API:  http://localhost:8080" -ForegroundColor White
Write-Host "Swagger UI:        http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "Actuator:          http://localhost:8080/actuator" -ForegroundColor White
Write-Host "Prometheus:        http://localhost:9090" -ForegroundColor White
Write-Host "Grafana:           http://localhost:3000 (admin/admin123)" -ForegroundColor White

Write-Host "`n=== Quick Test ===" -ForegroundColor Cyan
Write-Host "# Create a topic:" -ForegroundColor Gray
Write-Host 'Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/admin/topics" -ContentType "application/json" -Body ''{"name":"test-topic","numPartitions":3,"replicationFactor":1}''' -ForegroundColor Yellow

Write-Host "`n# Produce a message:" -ForegroundColor Gray
Write-Host 'Invoke-RestMethod -Method POST -Uri "http://localhost:8080/api/v1/producer/topics/test-topic" -ContentType "application/json" -Body ''{"key":"key1","value":"Hello Kafka!"}''' -ForegroundColor Yellow

Write-Host "`nUse 'docker-compose logs -f kafka-broker' to view logs" -ForegroundColor Gray
Write-Host "Use '.\stop-services.ps1' to stop all services" -ForegroundColor Gray
