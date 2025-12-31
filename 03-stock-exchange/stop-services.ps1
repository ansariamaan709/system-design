# Stock Exchange - Stop Services
# Stops all Docker services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stock Exchange - Stop Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (Test-Path "docker-compose.yml") {
    Write-Host "Stopping services with Docker Compose..." -ForegroundColor Yellow
    docker-compose down
} else {
    Write-Host "Stopping individual containers..." -ForegroundColor Yellow
    
    docker stop stockexchange-postgres 2>$null
    docker stop stockexchange-redis 2>$null
    docker stop stockexchange-kafka 2>$null
}

Write-Host ""
Write-Host "Services stopped!" -ForegroundColor Green
Write-Host ""
