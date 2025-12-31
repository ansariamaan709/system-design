# Setup database with schema
Write-Host "Setting up S3 database..." -ForegroundColor Green

# Check if PostgreSQL container is running
$container = docker ps --filter "name=s3-postgres" --format "{{.Names}}"
if (-not $container) {
    Write-Host "PostgreSQL container is not running. Start services first:" -ForegroundColor Red
    Write-Host "  .\start-services.ps1" -ForegroundColor Yellow
    exit 1
}

# Execute schema SQL
Write-Host "Applying schema..." -ForegroundColor Yellow
docker exec -i s3-postgres psql -U postgres -d s3db -f /docker-entrypoint-initdb.d/schema.sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "Database schema applied successfully!" -ForegroundColor Green
} else {
    Write-Host "Failed to apply database schema." -ForegroundColor Red
    exit 1
}

# Create Kafka topics
Write-Host "Creating Kafka topics..." -ForegroundColor Yellow
docker exec s3-redpanda rpk topic create s3-events --partitions 6 --replicas 1 2>$null
docker exec s3-redpanda rpk topic create s3-notifications --partitions 3 --replicas 1 2>$null
docker exec s3-redpanda rpk topic create s3-lifecycle --partitions 3 --replicas 1 2>$null

Write-Host "Kafka topics created!" -ForegroundColor Green
Write-Host ""
Write-Host "Database setup complete!" -ForegroundColor Green
