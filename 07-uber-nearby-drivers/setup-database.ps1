# Setup Uber Nearby Drivers Database
Write-Host "Setting up Uber Nearby Drivers database..." -ForegroundColor Green

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

# Check if PostgreSQL is running
$containerRunning = docker ps --filter "name=uber-postgres" --format "{{.Names}}"
if (-not $containerRunning) {
    Write-Host "PostgreSQL container is not running. Starting services..." -ForegroundColor Yellow
    docker-compose up -d postgres
    Start-Sleep -Seconds 10
}

# Apply schema
Write-Host "Applying database schema..." -ForegroundColor Yellow
$schemaPath = Join-Path $scriptPath "src\main\resources\schema.sql"

if (Test-Path $schemaPath) {
    docker exec -i uber-postgres psql -U postgres -d uber_nearby -f /docker-entrypoint-initdb.d/01-schema.sql
    Write-Host "Schema applied successfully!" -ForegroundColor Green
} else {
    Write-Host "Schema file not found at: $schemaPath" -ForegroundColor Red
    exit 1
}

Write-Host "`nDatabase setup complete!" -ForegroundColor Green
Write-Host "Connection string: jdbc:postgresql://localhost:5432/uber_nearby" -ForegroundColor Cyan
