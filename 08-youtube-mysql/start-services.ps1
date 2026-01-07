# Start YouTube MySQL infrastructure services
Write-Host "Starting YouTube MySQL infrastructure services..." -ForegroundColor Green

# Start Docker Compose
docker-compose up -d

# Wait for services to be healthy
Write-Host "Waiting for services to be healthy..." -ForegroundColor Yellow

# Wait for MySQL
Write-Host "Waiting for MySQL..." -ForegroundColor Cyan
$maxAttempts = 30
$attempt = 0
do {
    $attempt++
    $result = docker exec youtube-mysql mysqladmin ping -h localhost -u youtube -pyoutube_password 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "MySQL is ready!" -ForegroundColor Green
        break
    }
    Write-Host "Attempt $attempt/$maxAttempts - MySQL not ready yet..."
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

# Wait for Redis
Write-Host "Waiting for Redis..." -ForegroundColor Cyan
$attempt = 0
do {
    $attempt++
    $result = docker exec youtube-redis redis-cli ping 2>$null
    if ($result -eq "PONG") {
        Write-Host "Redis is ready!" -ForegroundColor Green
        break
    }
    Write-Host "Attempt $attempt/$maxAttempts - Redis not ready yet..."
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

# Wait for Kafka
Write-Host "Waiting for Kafka..." -ForegroundColor Cyan
Start-Sleep -Seconds 10

# Wait for Elasticsearch
Write-Host "Waiting for Elasticsearch (9200)..." -ForegroundColor Cyan
$attempt = 0
$maxAttempts = 30
do {
    $attempt++
    try {
        $resp = Invoke-RestMethod -Uri http://localhost:9200 -Method Get -ErrorAction Stop
        if ($resp) {
            Write-Host "Elasticsearch is ready!" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "Attempt $attempt/$maxAttempts - Elasticsearch not ready yet..."
    }
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

# Wait for Logstash (5044)
Write-Host "Waiting for Logstash (5044)..." -ForegroundColor Cyan
$attempt = 0
do {
    $attempt++
    $tcp = Test-NetConnection -ComputerName localhost -Port 5044 -WarningAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) {
        Write-Host "Logstash is reachable!" -ForegroundColor Green
        break
    }
    Write-Host "Attempt $attempt/$maxAttempts - Logstash not reachable yet..."
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

# Wait for Kibana
Write-Host "Waiting for Kibana (5601)..." -ForegroundColor Cyan
$attempt = 0
do {
    $attempt++
    try {
        $resp = Invoke-RestMethod -Uri http://localhost:5601/status -Method Get -ErrorAction Stop
        if ($resp) {
            Write-Host "Kibana is ready!" -ForegroundColor Green
            break
        }
    }
    catch {
        Write-Host "Attempt $attempt/$maxAttempts - Kibana not ready yet..."
    }
    Start-Sleep -Seconds 2
} while ($attempt -lt $maxAttempts)

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "All services are running!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Yellow
Write-Host "  MySQL:           localhost:3306" -ForegroundColor White
Write-Host "  Redis:           localhost:6379" -ForegroundColor White
Write-Host "  Kafka:           localhost:9092" -ForegroundColor White
Write-Host "  Kafka UI:        http://localhost:8081" -ForegroundColor White
Write-Host "  Redis Commander: http://localhost:8082" -ForegroundColor White
Write-Host "  phpMyAdmin:      http://localhost:8083" -ForegroundColor White
Write-Host "  Prometheus:      http://localhost:9090" -ForegroundColor White
Write-Host "  Grafana:         http://localhost:3000 (admin/admin)" -ForegroundColor White
Write-Host "  Elasticsearch:   http://localhost:9200" -ForegroundColor White
Write-Host "  Logstash:        localhost:5044" -ForegroundColor White
Write-Host "  Kibana:          http://localhost:5601" -ForegroundColor White
Write-Host ""
Write-Host "Run './run-local.ps1' to start the application" -ForegroundColor Cyan
