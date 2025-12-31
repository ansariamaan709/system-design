# Stop S3 infrastructure services
Write-Host "Stopping S3 infrastructure services..." -ForegroundColor Yellow

docker-compose down

Write-Host "All services stopped." -ForegroundColor Green
