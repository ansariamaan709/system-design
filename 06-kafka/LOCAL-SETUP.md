# Local Setup Guide - Kafka Clone

This guide explains how to set up and run the Kafka clone implementation locally.

## Prerequisites

- **Java 21** - Required for the Spring Boot application
- **Docker Desktop** - Required for PostgreSQL, Redis, and optional services
- **Maven 3.9+** - Or use the included Maven wrapper (`mvnw.cmd`)

## Quick Start

### Option 1: Docker Compose (Recommended)

Start all services including the Kafka broker:

```powershell
# Start all services
.\start-services.ps1

# View logs
docker-compose logs -f kafka-broker

# Stop all services
.\stop-services.ps1
```

### Option 2: Local Development

Run just the infrastructure and the Spring Boot app locally:

```powershell
# This starts PostgreSQL/Redis and runs the app with Maven
.\run-local.ps1
```

## Service URLs

| Service          | URL                                   | Credentials      |
| ---------------- | ------------------------------------- | ---------------- |
| Kafka Broker API | http://localhost:8080                 | -                |
| Swagger UI       | http://localhost:8080/swagger-ui.html | -                |
| Actuator         | http://localhost:8080/actuator        | -                |
| Prometheus       | http://localhost:9090                 | -                |
| Grafana          | http://localhost:3000                 | admin / admin123 |

## API Quick Reference

### Admin Operations

```powershell
# Create a topic
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/admin/topics" `
  -ContentType "application/json" `
  -Body '{"name":"my-topic","numPartitions":3,"replicationFactor":1}'

# List topics
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/topics"

# Describe topic
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/topics/my-topic"

# Delete topic
Invoke-RestMethod -Method DELETE -Uri "http://localhost:8080/api/v1/admin/topics/my-topic"
```

### Producer Operations

```powershell
# Produce a message
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/producer/topics/my-topic" `
  -ContentType "application/json" `
  -Body '{"key":"user-123","value":"Hello Kafka!"}'

# Produce a batch
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/producer/topics/my-topic/batch" `
  -ContentType "application/json" `
  -Body '{"records":[{"key":"k1","value":"v1"},{"key":"k2","value":"v2"}]}'
```

### Consumer Operations

```powershell
# Subscribe to topics
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/consumer/groups/my-group/subscribe" `
  -ContentType "application/json" `
  -Body '{"memberId":"consumer-1","clientId":"my-client","topics":["my-topic"]}'

# Fetch messages
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/consumer/groups/my-group/fetch" `
  -ContentType "application/json" `
  -Body '{"memberId":"consumer-1","offsets":[{"topic":"my-topic","partition":0,"offset":0}]}'

# Commit offsets
Invoke-RestMethod -Method POST `
  -Uri "http://localhost:8080/api/v1/consumer/groups/my-group/offsets/commit" `
  -ContentType "application/json" `
  -Body '{"memberId":"consumer-1","offsets":[{"topic":"my-topic","partition":0,"offset":10}]}'

# Get consumer lag
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/consumer/groups/my-group/lag"
```

## Configuration

### Application Properties

Key configuration options in `application-local.yml`:

```yaml
kafka:
  broker:
    id: 1
    host: localhost
    port: 9092
    rack: rack-1
  log:
    dir: ./logs/kafka-logs
    segment:
      bytes: 1073741824 # 1GB per segment
    retention:
      ms: 604800000 # 7 days
      bytes: -1 # unlimited
  producer:
    batch-size: 16384 # 16KB batch
    linger-ms: 0 # No delay
  consumer:
    fetch-max-bytes: 52428800 # 50MB
    max-poll-records: 500
```

### Environment Variables

| Variable                 | Description                | Default         |
| ------------------------ | -------------------------- | --------------- |
| `KAFKA_BROKER_ID`        | Unique broker identifier   | 1               |
| `KAFKA_LOG_DIR`          | Directory for log segments | /tmp/kafka-logs |
| `SPRING_DATASOURCE_URL`  | PostgreSQL connection URL  | -               |
| `SPRING_DATA_REDIS_HOST` | Redis host                 | localhost       |

## Troubleshooting

### Common Issues

1. **Port already in use**

   ```powershell
   # Find process using port 8080
   netstat -ano | findstr :8080
   # Kill process
   taskkill /PID <pid> /F
   ```

2. **Docker containers not starting**

   ```powershell
   # Check container logs
   docker-compose logs postgres
   docker-compose logs redis

   # Restart containers
   docker-compose down
   docker-compose up -d
   ```

3. **Database connection issues**

   ```powershell
   # Verify PostgreSQL is running
   docker exec kafka-postgres pg_isready -U kafka

   # Connect to database
   docker exec -it kafka-postgres psql -U kafka -d kafka
   ```

4. **Log directory permissions**
   ```powershell
   # Ensure log directory exists and is writable
   $logDir = ".\logs\kafka-logs"
   if (-not (Test-Path $logDir)) {
       New-Item -ItemType Directory -Path $logDir -Force
   }
   ```

### Health Checks

```powershell
# Application health
Invoke-RestMethod http://localhost:8080/actuator/health

# Database health
docker exec kafka-postgres pg_isready -U kafka

# Redis health
docker exec kafka-redis redis-cli ping
```

## Development Tips

### Running Tests

```powershell
# Run all tests
.\mvnw.cmd test

# Run specific test class
.\mvnw.cmd test -Dtest=TopicServiceTest

# Run with coverage
.\mvnw.cmd test jacoco:report
```

### Building the Application

```powershell
# Build without tests
.\mvnw.cmd clean package -DskipTests

# Build Docker image
docker build -t kafka-clone:latest .
```

### Debugging

1. Enable debug logging in `application-local.yml`:

   ```yaml
   logging:
     level:
       com.kafka: DEBUG
   ```

2. Use remote debugging:
   ```powershell
   $env:JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
   .\mvnw.cmd spring-boot:run
   ```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        REST API Layer                           │
│  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐    │
│  │ AdminCtrl   │  │ ProducerCtrl    │  │ ConsumerCtrl     │    │
│  └─────────────┘  └─────────────────┘  └──────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                       Service Layer                              │
│  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐    │
│  │TopicService │  │ProducerService  │  │ConsumerService   │    │
│  └─────────────┘  └─────────────────┘  └──────────────────┘    │
│  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐    │
│  │GroupCoord   │  │LogManager       │  │IdempotentMgr     │    │
│  └─────────────┘  └─────────────────┘  └──────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                       Storage Layer                              │
│  ┌─────────────┐  ┌─────────────────┐  ┌──────────────────┐    │
│  │    Log      │  │   LogSegment    │  │   RecordBatch    │    │
│  └─────────────┘  └─────────────────┘  └──────────────────┘    │
├─────────────────────────────────────────────────────────────────┤
│                     Data Store Layer                             │
│  ┌─────────────────────┐       ┌─────────────────────────┐     │
│  │  PostgreSQL         │       │  File System (Logs)     │     │
│  │  (Metadata)         │       │  (Message Data)         │     │
│  └─────────────────────┘       └─────────────────────────┘     │
└─────────────────────────────────────────────────────────────────┘
```
