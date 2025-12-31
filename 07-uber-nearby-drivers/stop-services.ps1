# Stop Uber Nearby Drivers services
Write-Host "Stopping Uber Nearby Drivers services..." -ForegroundColor Yellow

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptPath

docker-compose down

Write-Host "Services stopped." -ForegroundColor Green
