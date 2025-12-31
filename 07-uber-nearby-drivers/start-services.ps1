# Start Uber Nearby Drivers services
Write-Host "Starting Uber Nearby Drivers services..." -ForegroundColor Green

# Check if Docker is running
$dockerRunning = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Docker is not running. Please start Docker Desktop first." -ForegroundColor Red
    exit 1
}

# Navigate to project directory
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

# Start services
Write-Host "Starting PostgreSQL (PostGIS), Redis, Kafka, and monitoring services..." -ForegroundColor Yellow
docker-compose up -d

# Wait for services to be healthy
Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Check service status
Write-Host "`nService Status:" -ForegroundColor Cyan
docker-compose ps

Write-Host "`nServices started successfully!" -ForegroundColor Green
Write-Host "`nAvailable endpoints:" -ForegroundColor Cyan
Write-Host "  - PostgreSQL: localhost:5432 (user: postgres, password: postgres)" -ForegroundColor White
Write-Host "  - Redis: localhost:6379" -ForegroundColor White
Write-Host "  - Kafka: localhost:9092" -ForegroundColor White
Write-Host "  - Kafka UI: http://localhost:8081" -ForegroundColor White
Write-Host "  - Redis Commander: http://localhost:8082" -ForegroundColor White
Write-Host "  - Prometheus: http://localhost:9090" -ForegroundColor White
Write-Host "  - Grafana: http://localhost:3000 (admin/admin)" -ForegroundColor White
