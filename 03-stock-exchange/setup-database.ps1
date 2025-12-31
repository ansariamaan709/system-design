# Stock Exchange - Database Setup Script
# Creates PostgreSQL database and initializes schema

param(
    [string]$DbHost = "localhost",
    [string]$DbPort = "5432",
    [string]$DbName = "stockexchange",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "postgres"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stock Exchange - Database Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Set PGPASSWORD environment variable
$env:PGPASSWORD = $DbPassword

# Check if PostgreSQL is running
Write-Host "Checking PostgreSQL connection..." -ForegroundColor Yellow
$psqlTest = psql -h $DbHost -p $DbPort -U $DbUser -c "SELECT 1" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Cannot connect to PostgreSQL at ${DbHost}:${DbPort}" -ForegroundColor Red
    Write-Host "Please ensure PostgreSQL is running and credentials are correct." -ForegroundColor Red
    Write-Host ""
    Write-Host "If using Docker, run:" -ForegroundColor Yellow
    Write-Host "  docker run --name postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:15" -ForegroundColor White
    exit 1
}

Write-Host "PostgreSQL connection successful!" -ForegroundColor Green

# Check if database exists
Write-Host ""
Write-Host "Checking if database '$DbName' exists..." -ForegroundColor Yellow
$dbExists = psql -h $DbHost -p $DbPort -U $DbUser -tc "SELECT 1 FROM pg_database WHERE datname='$DbName'" 2>&1

if ($dbExists -match "1") {
    Write-Host "Database '$DbName' already exists." -ForegroundColor Green
    
    $response = Read-Host "Do you want to drop and recreate it? (y/N)"
    if ($response -eq 'y' -or $response -eq 'Y') {
        Write-Host "Dropping database '$DbName'..." -ForegroundColor Yellow
        psql -h $DbHost -p $DbPort -U $DbUser -c "DROP DATABASE IF EXISTS $DbName" 2>&1
        Write-Host "Creating database '$DbName'..." -ForegroundColor Yellow
        psql -h $DbHost -p $DbPort -U $DbUser -c "CREATE DATABASE $DbName" 2>&1
    }
}
else {
    Write-Host "Creating database '$DbName'..." -ForegroundColor Yellow
    psql -h $DbHost -p $DbPort -U $DbUser -c "CREATE DATABASE $DbName" 2>&1
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to create database" -ForegroundColor Red
    exit 1
}

# Run schema script
$schemaFile = Join-Path $PSScriptRoot "src\main\resources\schema.sql"
if (Test-Path $schemaFile) {
    Write-Host ""
    Write-Host "Running schema initialization..." -ForegroundColor Yellow
    psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -f $schemaFile 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Schema initialized successfully!" -ForegroundColor Green
    }
    else {
        Write-Host "WARNING: Some schema errors occurred (may be OK if tables exist)" -ForegroundColor Yellow
    }
}
else {
    Write-Host "WARNING: Schema file not found at $schemaFile" -ForegroundColor Yellow
}

# Verify tables
Write-Host ""
Write-Host "Verifying database tables..." -ForegroundColor Yellow
$tables = psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -tc "SELECT tablename FROM pg_tables WHERE schemaname='public'" 2>&1

Write-Host "Tables created:" -ForegroundColor Green
Write-Host $tables -ForegroundColor White

# Show sample data
Write-Host ""
Write-Host "Sample instruments:" -ForegroundColor Yellow
psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -c "SELECT symbol, name, type, previous_close FROM instruments LIMIT 10" 2>&1

Write-Host ""
Write-Host "Sample clients:" -ForegroundColor Yellow
psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -c "SELECT client_id, email, client_type, api_key FROM clients" 2>&1

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Database setup complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Connection details:" -ForegroundColor White
Write-Host "  Host:     $DbHost" -ForegroundColor Gray
Write-Host "  Port:     $DbPort" -ForegroundColor Gray
Write-Host "  Database: $DbName" -ForegroundColor Gray
Write-Host "  Username: $DbUser" -ForegroundColor Gray
Write-Host ""

# Clear password from environment
Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
