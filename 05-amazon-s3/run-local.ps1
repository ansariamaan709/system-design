# Run S3 application locally
Write-Host "Starting S3 Application..." -ForegroundColor Green

# Create storage directory if it doesn't exist
$storageDir = ".\data\storage"
if (-not (Test-Path $storageDir)) {
    New-Item -ItemType Directory -Force -Path $storageDir | Out-Null
    Write-Host "Created storage directory: $storageDir" -ForegroundColor Yellow
}

# Create temp directory if it doesn't exist
$tempDir = ".\data\temp"
if (-not (Test-Path $tempDir)) {
    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    Write-Host "Created temp directory: $tempDir" -ForegroundColor Yellow
}

# Check if Maven wrapper exists
if (-not (Test-Path ".\mvnw.cmd")) {
    Write-Host "Maven wrapper not found. Please run 'mvn wrapper:wrapper' first." -ForegroundColor Red
    exit 1
}

# Run with local profile
Write-Host ""
Write-Host "Application will be available at:" -ForegroundColor Cyan
Write-Host "  S3 API:     http://localhost:9000" -ForegroundColor Yellow
Write-Host "  Swagger UI: http://localhost:9000/swagger-ui.html" -ForegroundColor Yellow
Write-Host "  Actuator:   http://localhost:9000/actuator" -ForegroundColor Yellow
Write-Host ""

.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
