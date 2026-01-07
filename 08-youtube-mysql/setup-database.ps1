# Setup YouTube MySQL database
Write-Host "Setting up YouTube MySQL database..." -ForegroundColor Green

# Check if MySQL is running
$mysqlRunning = docker ps --filter "name=youtube-mysql" --format "{{.Names}}" 2>$null
if (-not $mysqlRunning) {
    Write-Host "MySQL container not running. Start services first with ./start-services.ps1" -ForegroundColor Red
    exit 1
}

# Run schema script
Write-Host "Applying schema..." -ForegroundColor Cyan
docker exec -i youtube-mysql mysql -u youtube -pyoutube_password youtube < src/main/resources/schema.sql

Write-Host "Database setup complete!" -ForegroundColor Green
