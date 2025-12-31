# Stop All Services for URL Shortener (Windows PowerShell)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stopping URL Shortener Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Stop Kafka
Write-Host "[1/3] Stopping Kafka..." -ForegroundColor Yellow
$kafkaProcesses = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*kafka*" }
if ($kafkaProcesses) {
    $kafkaProcesses | Stop-Process -Force
    Write-Host "✓ Kafka stopped" -ForegroundColor Green
} else {
    Write-Host "⚠ No Kafka process found" -ForegroundColor Yellow
}
Start-Sleep -Seconds 2

# Stop Zookeeper
Write-Host "[2/3] Stopping Zookeeper..." -ForegroundColor Yellow
$zookeeperProcesses = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*zookeeper*" }
if ($zookeeperProcesses) {
    $zookeeperProcesses | Stop-Process -Force
    Write-Host "✓ Zookeeper stopped" -ForegroundColor Green
} else {
    Write-Host "⚠ No Zookeeper process found" -ForegroundColor Yellow
}
Start-Sleep -Seconds 2

# Stop Redis
Write-Host "[3/3] Stopping Redis..." -ForegroundColor Yellow
try {
    redis-cli shutdown 2>&1 | Out-Null
    Write-Host "✓ Redis stopped" -ForegroundColor Green
} catch {
    # Try to kill process if shutdown command fails
    $redisProcess = Get-Process -Name redis-server -ErrorAction SilentlyContinue
    if ($redisProcess) {
        $redisProcess | Stop-Process -Force
        Write-Host "✓ Redis stopped (forced)" -ForegroundColor Green
    } else {
        Write-Host "⚠ No Redis process found" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "All Services Stopped!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Note: PostgreSQL service was not stopped (usually runs as Windows service)" -ForegroundColor Yellow
Write-Host "To stop PostgreSQL: net stop postgresql-x64-18" -ForegroundColor White
Write-Host ""
