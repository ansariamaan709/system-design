# Start All Services for URL Shortener (Windows PowerShell)
# Run this script to start Redis, Zookeeper, and Kafka

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting URL Shortener Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$KAFKA_HOME = "C:\Softwares\kafka\kafka_2.13-4.0.1"

# Check if Kafka directory exists
if (-Not (Test-Path $KAFKA_HOME)) {
    Write-Host "ERROR: Kafka not found at $KAFKA_HOME" -ForegroundColor Red
    Write-Host "Please update KAFKA_HOME variable in this script" -ForegroundColor Yellow
    exit 1
}

# 1. Start Redis
Write-Host "[1/3] Starting Redis Server..." -ForegroundColor Green
try {
    Start-Process powershell -ArgumentList "redis-server" -WindowStyle Normal
    Write-Host "✓ Redis started on localhost:6379" -ForegroundColor Green
} catch {
    Write-Host "✗ Failed to start Redis. Is it installed?" -ForegroundColor Red
    Write-Host "  Download from: https://github.com/tporadowski/redis/releases" -ForegroundColor Yellow
}
Start-Sleep -Seconds 3

# 2. Start Zookeeper
Write-Host "[2/3] Starting Zookeeper..." -ForegroundColor Green
$zookeeperCmd = "cd '$KAFKA_HOME'; .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties"
Start-Process powershell -ArgumentList $zookeeperCmd -WindowStyle Normal
Write-Host "✓ Zookeeper starting on localhost:2181" -ForegroundColor Green
Write-Host "  Waiting 15 seconds for Zookeeper to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 3. Start Kafka
Write-Host "[3/3] Starting Kafka Server..." -ForegroundColor Green
$kafkaCmd = "cd '$KAFKA_HOME'; .\bin\windows\kafka-server-start.bat .\config\server.properties"
Start-Process powershell -ArgumentList $kafkaCmd -WindowStyle Normal
Write-Host "✓ Kafka starting on localhost:9092" -ForegroundColor Green
Write-Host "  Waiting 15 seconds for Kafka to initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# Verify services
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Verifying Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check Redis
Write-Host "Checking Redis..." -ForegroundColor Yellow
try {
    $redisTest = redis-cli ping 2>&1
    if ($redisTest -like "*PONG*") {
        Write-Host "✓ Redis is running" -ForegroundColor Green
    } else {
        Write-Host "✗ Redis check failed" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Redis check failed" -ForegroundColor Red
}

# Check PostgreSQL
Write-Host "Checking PostgreSQL..." -ForegroundColor Yellow
try {
    $pgTest = psql -U postgres -c "SELECT version();" 2>&1
    if ($pgTest -like "*PostgreSQL*") {
        Write-Host "✓ PostgreSQL is running" -ForegroundColor Green
    } else {
        Write-Host "⚠ PostgreSQL might not be running" -ForegroundColor Yellow
    }
} catch {
    Write-Host "⚠ PostgreSQL check failed (might still be running)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Services Started!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Service Status:" -ForegroundColor White
Write-Host "  Redis:      localhost:6379" -ForegroundColor White
Write-Host "  Zookeeper:  localhost:2181" -ForegroundColor White
Write-Host "  Kafka:      localhost:9092" -ForegroundColor White
Write-Host "  PostgreSQL: localhost:5432 (should already be running)" -ForegroundColor White
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Yellow
Write-Host "  1. Ensure PostgreSQL is running (Windows service)" -ForegroundColor White
Write-Host "  2. Create database: psql -U postgres -c 'CREATE DATABASE urlshortener;'" -ForegroundColor White
Write-Host "  3. Run schema: psql -U postgres -d urlshortener -f src/main/resources/schema.sql" -ForegroundColor White
Write-Host "  4. Start application: mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -ForegroundColor White
Write-Host ""
Write-Host "To stop services, run: .\stop-services.ps1" -ForegroundColor Yellow
Write-Host ""
