# Stock Exchange - Start Services (Docker)
# Starts required infrastructure services using Docker

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stock Exchange - Start Services" -ForegroundColor Cyan  
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Docker
Write-Host "Checking Docker..." -ForegroundColor Yellow
docker --version 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Docker is not installed or not running" -ForegroundColor Red
    exit 1
}
Write-Host "Docker is available!" -ForegroundColor Green

# Start services with docker-compose
Write-Host ""
Write-Host "Starting services with Docker Compose..." -ForegroundColor Yellow

if (Test-Path "docker-compose.yml") {
    docker-compose up -d
}
else {
    Write-Host "docker-compose.yml not found, starting services individually..." -ForegroundColor Yellow
    
    # PostgreSQL
    Write-Host ""
    Write-Host "Starting PostgreSQL..." -ForegroundColor Yellow
    $pgExists = docker ps -a --filter "name=stockexchange-postgres" --format "{{.Names}}"
    if ($pgExists) {
        docker start stockexchange-postgres
    }
    else {
        docker run -d `
            --name stockexchange-postgres `
            -e POSTGRES_PASSWORD=postgres `
            -e POSTGRES_DB=stockexchange `
            -p 5432:5432 `
            -v stockexchange-pg-data:/var/lib/postgresql/data `
            postgres:15
    }
    
    # Redis
    Write-Host ""
    Write-Host "Starting Redis..." -ForegroundColor Yellow
    $redisExists = docker ps -a --filter "name=stockexchange-redis" --format "{{.Names}}"
    if ($redisExists) {
        docker start stockexchange-redis
    }
    else {
        docker run -d `
            --name stockexchange-redis `
            -p 6379:6379 `
            redis:7-alpine
    }
    
    # Kafka (using Redpanda for simplicity)
    Write-Host ""
    Write-Host "Starting Kafka (Redpanda)..." -ForegroundColor Yellow
    $kafkaExists = docker ps -a --filter "name=stockexchange-kafka" --format "{{.Names}}"
    if ($kafkaExists) {
        docker start stockexchange-kafka
    }
    else {
        docker run -d `
            --name stockexchange-kafka `
            -p 9092:9092 `
            -p 9644:9644 `
            docker.redpanda.com/redpandadata/redpanda:latest `
            start --smp 1 --memory 512M --overprovisioned `
            --kafka-addr PLAINTEXT://0.0.0.0:9092 `
            --advertise-kafka-addr PLAINTEXT://localhost:9092
    }
}

# Wait for services
Write-Host ""
Write-Host "Waiting for services to be ready..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Check services
Write-Host ""
Write-Host "Service Status:" -ForegroundColor Yellow
Write-Host "---------------" -ForegroundColor Yellow

# Check PostgreSQL
$pgRunning = docker ps --filter "name=stockexchange-postgres" --filter "status=running" --format "{{.Names}}"
if ($pgRunning) {
    Write-Host "PostgreSQL:  Running (localhost:5432)" -ForegroundColor Green
}
else {
    Write-Host "PostgreSQL:  Not Running" -ForegroundColor Red
}

# Check Redis
$redisRunning = docker ps --filter "name=stockexchange-redis" --filter "status=running" --format "{{.Names}}"
if ($redisRunning) {
    Write-Host "Redis:       Running (localhost:6379)" -ForegroundColor Green
}
else {
    Write-Host "Redis:       Not Running" -ForegroundColor Red
}

# Check Kafka
$kafkaRunning = docker ps --filter "name=stockexchange-kafka" --filter "status=running" --format "{{.Names}}"
if ($kafkaRunning) {
    Write-Host "Kafka:       Running (localhost:9092)" -ForegroundColor Green
}
else {
    Write-Host "Kafka:       Not Running" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Services started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor White
Write-Host "  1. Run .\setup-database.ps1 to initialize the database" -ForegroundColor Gray
Write-Host "  2. Run .\run-local.ps1 to start the application" -ForegroundColor Gray
Write-Host ""
