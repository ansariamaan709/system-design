# PowerShell script to setup the database

Write-Host "=== Database Setup ===" -ForegroundColor Cyan

# Check if Docker is running
try {
    docker info | Out-Null
}
catch {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Start PostgreSQL if not running
$pgContainer = docker ps --filter "name=kafka-postgres" --format "{{.Names}}" 2>$null
if (-not $pgContainer) {
    Write-Host "Starting PostgreSQL..." -ForegroundColor Yellow
    docker-compose up -d postgres
    
    # Wait for PostgreSQL
    $maxRetries = 30
    $retryCount = 0
    do {
        Start-Sleep -Seconds 2
        $result = docker exec kafka-postgres pg_isready -U kafka 2>$null
        $retryCount++
    } while ($LASTEXITCODE -ne 0 -and $retryCount -lt $maxRetries)
}

Write-Host "[OK] PostgreSQL is ready" -ForegroundColor Green

# Apply schema
Write-Host "`nApplying database schema..." -ForegroundColor Yellow

# Copy schema file to container and execute
docker cp .\src\main\resources\schema.sql kafka-postgres:/tmp/schema.sql
docker exec kafka-postgres psql -U kafka -d kafka -f /tmp/schema.sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "[OK] Schema applied successfully" -ForegroundColor Green
}
else {
    Write-Host "[WARN] Schema may have already been applied or there were errors" -ForegroundColor Yellow
}

# Verify tables
Write-Host "`nVerifying tables..." -ForegroundColor Yellow
$tables = docker exec kafka-postgres psql -U kafka -d kafka -t -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public';"
Write-Host $tables -ForegroundColor Gray

Write-Host "`n[OK] Database setup complete" -ForegroundColor Green
