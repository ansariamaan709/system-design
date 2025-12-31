# Stock Exchange - Local Development Runner
# Starts the application with local profile

param(
    [switch]$SkipBuild,
    [switch]$Debug,
    [string]$Profile = "local"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stock Exchange - Local Runner" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check Java version
Write-Host "Checking Java version..." -ForegroundColor Yellow
$javaVersion = java -version 2>&1 | Select-String "version"
Write-Host $javaVersion -ForegroundColor White

# Build if not skipped
if (-not $SkipBuild) {
    Write-Host ""
    Write-Host "Building application..." -ForegroundColor Yellow
    
    if (Test-Path "mvnw.cmd") {
        .\mvnw.cmd clean package -DskipTests
    }
    else {
        mvn clean package -DskipTests
    }
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Build failed!" -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Build successful!" -ForegroundColor Green
}

# Find JAR file
$jarFile = Get-ChildItem -Path "target" -Filter "*.jar" | Where-Object { $_.Name -notlike "*-sources*" } | Select-Object -First 1

if (-not $jarFile) {
    Write-Host "ERROR: JAR file not found in target directory" -ForegroundColor Red
    Write-Host "Please run without -SkipBuild flag" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "Starting Stock Exchange..." -ForegroundColor Yellow
Write-Host "JAR: $($jarFile.Name)" -ForegroundColor Gray
Write-Host "Profile: $Profile" -ForegroundColor Gray
Write-Host ""

# JVM options for low latency
$jvmOpts = @(
    "-Xms1g",
    "-Xmx1g",
    "-XX:+UseZGC",
    "-XX:+AlwaysPreTouch"
)

if ($Debug) {
    $jvmOpts += "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
    Write-Host "Debug mode enabled on port 5005" -ForegroundColor Yellow
}

$jvmOptsString = $jvmOpts -join " "

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Application starting..." -ForegroundColor Green
Write-Host "  API:     http://localhost:8080" -ForegroundColor White
Write-Host "  Swagger: http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  Health:  http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  Metrics: http://localhost:8080/actuator/prometheus" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press Ctrl+C to stop" -ForegroundColor Yellow
Write-Host ""

# Run the application
$env:SPRING_PROFILES_ACTIVE = $Profile
java $jvmOpts -jar $jarFile.FullName
