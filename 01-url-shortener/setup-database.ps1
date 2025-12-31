# Setup Database for URL Shortener (Windows PowerShell)
# This script creates the database and runs the schema

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "URL Shortener - Database Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$DB_NAME = "urlshortener"
$DB_USER = "postgres"
$SCHEMA_FILE = "src\main\resources\schema.sql"

# Check if schema file exists
if (-Not (Test-Path $SCHEMA_FILE)) {
    Write-Host "ERROR: Schema file not found at $SCHEMA_FILE" -ForegroundColor Red
    Write-Host "Please run this script from the project root directory" -ForegroundColor Yellow
    exit 1
}

Write-Host "Database Name: $DB_NAME" -ForegroundColor White
Write-Host "Database User: $DB_USER" -ForegroundColor White
Write-Host ""

# Step 1: Create Database
Write-Host "[Step 1/3] Creating database '$DB_NAME'..." -ForegroundColor Yellow
$createDbQuery = "SELECT 1 FROM pg_database WHERE datname='$DB_NAME';"
$dbExists = psql -U $DB_USER -t -c $createDbQuery 2>&1

if ($dbExists -like "*1*") {
    Write-Host "⚠ Database '$DB_NAME' already exists" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Do you want to drop and recreate it? (yes/no)"
    if ($response -eq "yes") {
        Write-Host "Dropping existing database..." -ForegroundColor Yellow
        psql -U $DB_USER -c "DROP DATABASE $DB_NAME;" 2>&1 | Out-Null
        psql -U $DB_USER -c "CREATE DATABASE $DB_NAME;" 2>&1 | Out-Null
        Write-Host "✓ Database recreated" -ForegroundColor Green
    } else {
        Write-Host "Keeping existing database" -ForegroundColor Yellow
    }
} else {
    psql -U $DB_USER -c "CREATE DATABASE $DB_NAME;" 2>&1 | Out-Null
    Write-Host "✓ Database created" -ForegroundColor Green
}

# Step 2: Run Schema
Write-Host ""
Write-Host "[Step 2/3] Running database schema..." -ForegroundColor Yellow
psql -U $DB_USER -d $DB_NAME -f $SCHEMA_FILE 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✓ Schema created successfully" -ForegroundColor Green
} else {
    Write-Host "✗ Error running schema" -ForegroundColor Red
    Write-Host "Please check the error messages above" -ForegroundColor Yellow
}

# Step 3: Verify Tables
Write-Host ""
Write-Host "[Step 3/3] Verifying tables..." -ForegroundColor Yellow
$tables = psql -U $DB_USER -d $DB_NAME -c "\dt" 2>&1

if ($tables -like "*urls*" -and $tables -like "*click_events*") {
    Write-Host "✓ Tables created successfully:" -ForegroundColor Green
    psql -U $DB_USER -d $DB_NAME -c "\dt"
} else {
    Write-Host "✗ Tables verification failed" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Database Setup Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Connection Details:" -ForegroundColor White
Write-Host "  Host:     localhost" -ForegroundColor White
Write-Host "  Port:     5432" -ForegroundColor White
Write-Host "  Database: $DB_NAME" -ForegroundColor White
Write-Host "  User:     $DB_USER" -ForegroundColor White
Write-Host ""
Write-Host "Update application-local.yml with your PostgreSQL password" -ForegroundColor Yellow
Write-Host ""
Write-Host "Next: Start the application with:" -ForegroundColor Yellow
Write-Host "  mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local" -ForegroundColor White
Write-Host ""
