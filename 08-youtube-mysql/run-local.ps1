# Run YouTube MySQL application locally
Write-Host "Starting YouTube MySQL application..." -ForegroundColor Green

# Check if services are running
$mysqlRunning = docker ps --filter "name=youtube-mysql" --format "{{.Names}}" 2>$null
if (-not $mysqlRunning) {
    Write-Host "Infrastructure services not running. Starting them first..." -ForegroundColor Yellow
    .\start-services.ps1
}

# Run the Spring Boot application
Write-Host "Starting Spring Boot application with local profile..." -ForegroundColor Cyan
.\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
