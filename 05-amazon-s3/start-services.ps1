# Start infrastructure services for S3 Clone
Write-Host "Starting S3 infrastructure services..." -ForegroundColor Green

# Check if Docker is running
try {
    docker info | Out-Null
}
catch {
    Write-Host "Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Start infrastructure services only
docker-compose up -d postgres redis redpanda

Write-Host "Waiting for services to be healthy..." -ForegroundColor Yellow

# Wait for PostgreSQL
$maxRetries = 30
$retryCount = 0
while ($retryCount -lt $maxRetries) {
    $result = docker exec s3-postgres pg_isready -U postgres 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "PostgreSQL is ready!" -ForegroundColor Green
        break
    }
    $retryCount++
    Write-Host "Waiting for PostgreSQL... ($retryCount/$maxRetries)"
    Start-Sleep -Seconds 2
}

# Wait for Redis
$retryCount = 0
while ($retryCount -lt $maxRetries) {
    $result = docker exec s3-redis redis-cli ping 2>$null
    if ($result -eq "PONG") {
        Write-Host "Redis is ready!" -ForegroundColor Green
        break
    }
    $retryCount++
    Write-Host "Waiting for Redis... ($retryCount/$maxRetries)"
    Start-Sleep -Seconds 2
}

# Wait for Redpanda
$retryCount = 0
while ($retryCount -lt $maxRetries) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:9644/v1/status/ready" -UseBasicParsing -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Host "Redpanda is ready!" -ForegroundColor Green
            break
        }
    }
    catch {
        # Ignore errors
    }
    $retryCount++
    Write-Host "Waiting for Redpanda... ($retryCount/$maxRetries)"
    Start-Sleep -Seconds 2
}

Write-Host ""
Write-Host "All services are running!" -ForegroundColor Green
Write-Host ""
Write-Host "Service URLs:" -ForegroundColor Cyan
Write-Host "  PostgreSQL: localhost:5432 (s3db)"
Write-Host "  Redis:      localhost:6379"
Write-Host "  Redpanda:   localhost:9092 (Kafka API)"
Write-Host ""
Write-Host "To run the S3 application locally:"
Write-Host "  .\run-local.ps1" -ForegroundColor Yellow
