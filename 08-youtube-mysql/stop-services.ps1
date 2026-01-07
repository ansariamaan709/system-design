# Stop YouTube MySQL infrastructure services
Write-Host "Stopping YouTube MySQL infrastructure services..." -ForegroundColor Yellow

docker-compose down

Write-Host "All services stopped!" -ForegroundColor Green
