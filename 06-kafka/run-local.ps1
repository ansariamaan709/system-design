# PowerShell script to run the Kafka clone application locally

Write-Host "=== Kafka Clone - Local Development Setup ===" -ForegroundColor Cyan

# Check if Docker is running
try {
    docker info | Out-Null
    Write-Host "[OK] Docker is running" -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Start infrastructure services
Write-Host "`nStarting infrastructure services (PostgreSQL, Redis)..." -ForegroundColor Yellow
docker-compose up -d postgres redis

# Wait for PostgreSQL to be ready
Write-Host "Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
$maxRetries = 30
$retryCount = 0
do {
    Start-Sleep -Seconds 2
    $result = docker exec kafka-postgres pg_isready -U kafka 2>$null
    $retryCount++
} while ($LASTEXITCODE -ne 0 -and $retryCount -lt $maxRetries)

if ($retryCount -ge $maxRetries) {
    Write-Host "[ERROR] PostgreSQL failed to start" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] PostgreSQL is ready" -ForegroundColor Green

# Wait for Redis to be ready
Write-Host "Waiting for Redis to be ready..." -ForegroundColor Yellow
$retryCount = 0
do {
    Start-Sleep -Seconds 1
    $result = docker exec kafka-redis redis-cli ping 2>$null
    $retryCount++
} while ($result -ne "PONG" -and $retryCount -lt $maxRetries)

if ($retryCount -ge $maxRetries) {
    Write-Host "[ERROR] Redis failed to start" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Redis is ready" -ForegroundColor Green

# Create log directory
$logDir = ".\logs\kafka-logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    Write-Host "[OK] Created log directory: $logDir" -ForegroundColor Green
}

# Run the Spring Boot application
Write-Host "`nStarting Kafka Broker application..." -ForegroundColor Yellow
Write-Host "Press Ctrl+C to stop`n" -ForegroundColor Gray

$env:SPRING_PROFILES_ACTIVE = "local"
$env:KAFKA_LOG_DIR = (Resolve-Path $logDir).Path

# Check if Maven wrapper exists
if (Test-Path ".\mvnw.cmd") {
    .\mvnw.cmd spring-boot:run
}
else {
    Write-Host "[ERROR] Maven wrapper not found. Run 'mvn wrapper:wrapper' first." -ForegroundColor Red
    exit 1
}
