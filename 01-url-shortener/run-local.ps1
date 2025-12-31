# Complete Setup Script - Run Everything at Once
# This script sets up the database, starts services, and runs the application

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "URL Shortener - Complete Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "This script will:" -ForegroundColor Yellow
Write-Host "  1. Setup PostgreSQL database" -ForegroundColor White
Write-Host "  2. Start Redis, Zookeeper, and Kafka" -ForegroundColor White
Write-Host "  3. Run the Spring Boot application" -ForegroundColor White
Write-Host ""

$response = Read-Host "Continue? (yes/no)"
if ($response -ne "yes") {
    Write-Host "Setup cancelled" -ForegroundColor Yellow
    exit 0
}

# Step 1: Setup Database
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 1: Database Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
& .\setup-database.ps1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Database setup failed. Please fix errors and try again." -ForegroundColor Red
    exit 1
}

# Step 2: Start Services
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 2: Starting Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
& .\start-services.ps1

# Wait for services to fully start
Write-Host ""
Write-Host "Waiting 10 seconds for services to fully initialize..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Step 3: Build the application
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 3: Building Application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Running Maven build (skipping tests)..." -ForegroundColor Yellow
.\mvnw.cmd clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed. Please fix errors and try again." -ForegroundColor Red
    exit 1
}

Write-Host "✓ Build successful" -ForegroundColor Green

# Step 4: Run the application
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Step 4: Starting Application" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting URL Shortener on http://localhost:8080" -ForegroundColor Green
Write-Host ""
Write-Host "Available endpoints:" -ForegroundColor White
Write-Host "  API:        http://localhost:8080/api/v1/urls" -ForegroundColor White
Write-Host "  Swagger:    http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  Health:     http://localhost:8080/api/v1/health" -ForegroundColor White
Write-Host "  Actuator:   http://localhost:8080/actuator" -ForegroundColor White
Write-Host ""
Write-Host "Press Ctrl+C to stop the application" -ForegroundColor Yellow
Write-Host ""

.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
