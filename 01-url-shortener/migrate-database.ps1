# Migration Script - Update short_code column size
# This script updates the database to support longer short codes

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Database Migration - Short Code Column" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# PostgreSQL connection details
$dbHost = "localhost"
$dbPort = "5432"
$dbName = "urlshortener"
$dbUser = "postgres"

Write-Host "This will update the short_code column from VARCHAR(10) to VARCHAR(20)" -ForegroundColor Yellow
Write-Host ""
$dbPassword = Read-Host "Enter PostgreSQL password for user '$dbUser'" -AsSecureString
$plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($dbPassword))

Write-Host ""
Write-Host "Running migration..." -ForegroundColor Yellow

$env:PGPASSWORD = $plainPassword

try {
    # Run the migration script
    $migrationFile = Join-Path $PSScriptRoot "src\main\resources\migrate-short-code.sql"
    
    psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -f $migrationFile
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host ""
        Write-Host "✓ Migration completed successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "The short_code column has been updated to VARCHAR(20)" -ForegroundColor Green
    } else {
        Write-Host ""
        Write-Host "✗ Migration failed!" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error: $_" -ForegroundColor Red
    exit 1
} finally {
    $env:PGPASSWORD = $null
}

Write-Host ""
Write-Host "You can now restart your application" -ForegroundColor Cyan
