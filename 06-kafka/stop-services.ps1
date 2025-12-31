# PowerShell script to stop all services

Write-Host "=== Stopping Kafka Clone Services ===" -ForegroundColor Cyan

# Stop and remove containers
Write-Host "Stopping containers..." -ForegroundColor Yellow
docker-compose down

Write-Host "`n[OK] All services stopped" -ForegroundColor Green
Write-Host "Use 'docker-compose down -v' to also remove volumes (data will be lost)" -ForegroundColor Gray
