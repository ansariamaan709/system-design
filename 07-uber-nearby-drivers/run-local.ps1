# Run Uber Nearby Drivers application locally
Write-Host "Starting Uber Nearby Drivers application..." -ForegroundColor Green

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

# Check if services are running
$redisRunning = docker ps --filter "name=uber-redis" --format "{{.Names}}"
if (-not $redisRunning) {
    Write-Host "Services not running. Please run start-services.ps1 first." -ForegroundColor Red
    exit 1
}

# Run with local profile
Write-Host "Running with local profile..." -ForegroundColor Yellow
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
