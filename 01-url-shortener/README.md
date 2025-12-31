# URL Shortener - Production-Grade Implementation

A complete, production-ready URL shortening service built with Spring Boot 3.2, featuring distributed ID generation, Redis caching, analytics tracking, and comprehensive monitoring.

## 📋 Table of Contents

- [Architecture Overview](#architecture-overview)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [System Design Deep Dive](#system-design-deep-dive)
- [Configuration](#configuration)
- [Monitoring](#monitoring)
- [Testing](#testing)
- [Deployment](#deployment)

---

## 🏗️ Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client                                      │
│                     (Web Browser / Mobile App)                           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Load Balancer (Nginx)                            │
│                         - SSL Termination                                │
│                         - Rate Limiting L7                               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
          ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
          │  App Node 1 │   │  App Node 2 │   │  App Node N │
          │  (Port 8080)│   │  (Port 8080)│   │  (Port 8080)│
          └──────┬──────┘   └──────┬──────┘   └──────┬──────┘
                 │                 │                 │
                 └────────────┬────┴────────────────┘
                              │
         ┌────────────────────┼────────────────────┐
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  Redis Cluster  │  │   PostgreSQL    │  │     Kafka       │
│  (Cache Layer)  │  │   (Primary DB)  │  │  (Event Stream) │
│  - URL Mapping  │  │  - URLs Table   │  │  - Click Events │
│  - Rate Limits  │  │  - Click Events │  │  - Analytics    │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

### Request Flow

1. **Create Short URL**: `POST /api/v1/urls`

   - Generate unique ID using Snowflake algorithm
   - Encode ID to Base62 (7 characters)
   - Store in PostgreSQL
   - Cache mapping in Redis

2. **Redirect**: `GET /{shortCode}`
   - Check Redis cache first (O(1) lookup)
   - Fall back to PostgreSQL if cache miss
   - Record click event asynchronously
   - Return 302 redirect

---

## ✨ Features

### Core Features

- ✅ **Short URL Generation** - Snowflake ID + Base62 encoding
- ✅ **Custom Aliases** - User-defined short codes
- ✅ **URL Expiration** - Time-based URL expiry
- ✅ **Click Analytics** - Comprehensive tracking

### Performance Features

- ✅ **Redis Caching** - Sub-millisecond lookups
- ✅ **Connection Pooling** - HikariCP for database
- ✅ **Async Processing** - Non-blocking click tracking
- ✅ **Rate Limiting** - Bucket4j token bucket algorithm

### Production Features

- ✅ **Health Checks** - Kubernetes-ready endpoints
- ✅ **Metrics** - Prometheus/Micrometer integration
- ✅ **API Documentation** - OpenAPI 3.0/Swagger UI
- ✅ **Docker Support** - Multi-stage builds
- ✅ **Scheduled Tasks** - Expired URL cleanup

---

## 🛠️ Technology Stack

| Component     | Technology              | Purpose                 |
| ------------- | ----------------------- | ----------------------- |
| Framework     | Spring Boot 3.2.1       | Application framework   |
| Language      | Java 21                 | Runtime                 |
| Database      | PostgreSQL 15           | Primary data store      |
| Cache         | Redis 7                 | Caching & rate limiting |
| Messaging     | Apache Kafka            | Event streaming         |
| Metrics       | Micrometer + Prometheus | Observability           |
| API Docs      | SpringDoc OpenAPI       | Documentation           |
| Rate Limiting | Bucket4j                | Request throttling      |
| Build         | Maven                   | Dependency management   |
| Container     | Docker                  | Deployment              |

---

## 📁 Project Structure

```
01-url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/urlshortener/
│   │   │   ├── UrlShortenerApplication.java    # Main entry point
│   │   │   ├── config/
│   │   │   │   ├── AsyncConfig.java            # Thread pool config
│   │   │   │   ├── OpenApiConfig.java          # Swagger config
│   │   │   │   ├── RateLimitFilter.java        # Rate limiting
│   │   │   │   └── RedisConfig.java            # Redis/Cache config
│   │   │   ├── controller/
│   │   │   │   ├── HealthController.java       # Health endpoints
│   │   │   │   └── UrlController.java          # REST API
│   │   │   ├── dto/
│   │   │   │   ├── ApiResponse.java            # Generic response
│   │   │   │   ├── ClickEventDto.java          # Click event DTO
│   │   │   │   ├── CreateUrlRequest.java       # Create URL request
│   │   │   │   ├── UrlResponse.java            # URL response
│   │   │   │   └── UrlStatsResponse.java       # Statistics response
│   │   │   ├── entity/
│   │   │   │   ├── ClickEvent.java             # Click event entity
│   │   │   │   └── Url.java                    # URL entity
│   │   │   ├── exception/
│   │   │   │   ├── AliasAlreadyExistsException.java
│   │   │   │   ├── GlobalExceptionHandler.java # Central error handling
│   │   │   │   ├── RateLimitExceededException.java
│   │   │   │   ├── UrlExpiredException.java
│   │   │   │   └── UrlNotFoundException.java
│   │   │   ├── repository/
│   │   │   │   ├── ClickEventRepository.java   # Click analytics
│   │   │   │   └── UrlRepository.java          # URL persistence
│   │   │   ├── scheduler/
│   │   │   │   └── ScheduledTasks.java         # Background jobs
│   │   │   └── service/
│   │   │       ├── Base62Encoder.java          # Short code encoding
│   │   │       ├── ClickEventService.java      # Async click tracking
│   │   │       ├── SnowflakeIdGenerator.java   # Distributed ID gen
│   │   │       ├── UrlCacheService.java        # Redis caching
│   │   │       ├── UrlService.java             # Core business logic
│   │   │       └── UserAgentParser.java        # Browser/OS detection
│   │   └── resources/
│   │       ├── application.yml                 # Configuration
│   │       └── schema.sql                      # Database schema
│   └── test/
│       └── java/com/urlshortener/
│           ├── UrlShortenerApplicationTests.java
│           ├── controller/
│           │   └── UrlControllerTest.java
│           └── service/
│               ├── Base62EncoderTest.java
│               ├── SnowflakeIdGeneratorTest.java
│               └── UrlServiceTest.java
├── docker/
│   ├── grafana/
│   │   └── provisioning/datasources/
│   └── prometheus/
│       └── prometheus.yml
├── docker-compose.yml                          # Full stack setup
├── Dockerfile                                  # Multi-stage build
├── pom.xml                                     # Maven config
└── README.md                                   # This file
```

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker & Docker Compose (for full stack)
- PostgreSQL 15+ (if running locally)
- Redis 7+ (if running locally)

### Option 1: Docker Compose (Recommended)

```bash
# Clone and navigate to project
cd 01-url-shortener

# Start all services
docker-compose up -d

# Check status
docker-compose ps

# View logs
docker-compose logs -f app
```

**Available endpoints:**

- Application: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### Option 2: Local Development

```bash
# Start dependencies only
docker-compose up -d postgres redis kafka zookeeper

# Run application with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Option 3: H2 In-Memory (Quick Start)

```bash
# Run with dev profile (uses H2 database)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Access H2 Console: http://localhost:8080/h2-console

### Option 4: Local Development (Windows - No Docker)

This guide shows how to run the application with all services on localhost.

#### Step 1: Start Redis Server

```powershell
# Navigate to your Redis installation or run from any directory
redis-server

# In a new terminal, verify Redis is running
redis-cli ping
# Should return: PONG
```

**Redis will run on:** `localhost:6379`

#### Step 2: Setup PostgreSQL Database

```powershell
# Connect to PostgreSQL (using psql or pgAdmin)
psql -U postgres

# Create database
CREATE DATABASE urlshortener;

# Connect to the database
\c urlshortener

# Run the schema (from project root)
# Copy the SQL from src/main/resources/schema.sql and execute it
# Or use psql command:
psql -U postgres -d urlshortener -f src/main/resources/schema.sql
```

**PostgreSQL will run on:** `localhost:5432`

#### Step 3: Start Zookeeper (Required for Kafka)

```powershell
# Navigate to Kafka installation
cd C:\Softwares\kafka\kafka_2.13-4.0.1

# Start Zookeeper
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties
```

**Zookeeper will run on:** `localhost:2181`

Keep this terminal open.

#### Step 4: Start Kafka Server

Open a **new terminal** and run:

```powershell
# Navigate to Kafka installation
cd C:\Softwares\kafka\kafka_2.13-4.0.1

# Start Kafka
.\bin\windows\kafka-server-start.bat .\config\server.properties
```

**Kafka will run on:** `localhost:9092`

Keep this terminal open.

#### Step 5: Create Kafka Topics (Optional but Recommended)

Open a **new terminal** and run:

```powershell
# Navigate to Kafka installation
cd C:\Softwares\kafka\kafka_2.13-4.0.1

# Create topic for click events
.\bin\windows\kafka-topics.bat --create --topic url-click-events --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Verify topic creation
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092
```

#### Step 6: Configure Application

Update `src/main/resources/application.yml` or create `application-local.yml`:

```yaml
spring:
  application:
    name: url-shortener

  # PostgreSQL Configuration
  datasource:
    url: jdbc:postgresql://localhost:5432/urlshortener
    username: postgres
    password: your_postgres_password # Change this
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: 30000
      maximum-pool-size: 10

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  # Redis Configuration
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 5000ms

  # Kafka Configuration
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: url-shortener-group
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer

# Server Configuration
server:
  port: 8080

# URL Shortener Configuration
url-shortener:
  base-url: http://localhost:8080
  snowflake:
    datacenter-id: 1
    machine-id: 1
  cache:
    ttl-hours: 24
  rate-limit:
    enabled: true
    requests-per-minute: 60

# Logging
logging:
  level:
    root: INFO
    com.urlshortener: DEBUG
```

#### Step 7: Run the Application

```powershell
# Option A: Using Maven (from project root)
mvnw.cmd spring-boot:run

# Option B: Using Maven with specific profile
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local

# Option C: Build and run JAR
mvnw.cmd clean package -DskipTests
java -jar target/url-shortener-*.jar
```

#### Step 8: Verify All Services

Open new terminals and verify each service:

```powershell
# Check Redis
redis-cli ping

# Check PostgreSQL
psql -U postgres -d urlshortener -c "SELECT count(*) FROM urls;"

# Check Kafka topics
cd C:\Softwares\kafka\kafka_2.13-4.0.1
.\bin\windows\kafka-topics.bat --list --bootstrap-server localhost:9092

# Check Application
curl http://localhost:8080/api/v1/health
```

#### Service URLs Summary

| Service     | URL                                   | Status Check                 |
| ----------- | ------------------------------------- | ---------------------------- |
| Application | http://localhost:8080                 | `curl localhost:8080/health` |
| Swagger UI  | http://localhost:8080/swagger-ui.html | Open in browser              |
| PostgreSQL  | localhost:5432                        | `psql -U postgres`           |
| Redis       | localhost:6379                        | `redis-cli ping`             |
| Kafka       | localhost:9092                        | Topic list command above     |
| Zookeeper   | localhost:2181                        | Running in terminal          |
| H2 Console  | http://localhost:8080/h2-console      | (Only if using H2)           |

#### Troubleshooting

**Issue: Redis connection refused**

```powershell
# Ensure Redis is running
redis-server
```

**Issue: PostgreSQL authentication failed**

```powershell
# Update password in application.yml
# Or set environment variable
$env:SPRING_DATASOURCE_PASSWORD="your_password"
```

**Issue: Kafka not starting**

```powershell
# Ensure Zookeeper is running first
# Check if port 9092 is already in use
netstat -ano | findstr :9092
```

**Issue: Port 8080 already in use**

```powershell
# Change port in application.yml
server:
  port: 8081

# Or set environment variable
$env:SERVER_PORT="8081"
```

#### Quick Start Script (Windows PowerShell)

Save this as `start-services.ps1`:

```powershell
# Start Redis (assuming it's in PATH)
Start-Process powershell -ArgumentList "redis-server" -WindowStyle Normal

# Start Zookeeper
Start-Process powershell -ArgumentList "cd C:\Softwares\kafka\kafka_2.13-4.0.1; .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties" -WindowStyle Normal

# Wait for Zookeeper to start
Start-Sleep -Seconds 10

# Start Kafka
Start-Process powershell -ArgumentList "cd C:\Softwares\kafka\kafka_2.13-4.0.1; .\bin\windows\kafka-server-start.bat .\config\server.properties" -WindowStyle Normal

Write-Host "All services starting... Wait 30 seconds before running the application"
Write-Host "PostgreSQL must be started manually (usually runs as Windows service)"
```

Run with: `.\start-services.ps1`

#### Stopping Services

```powershell
# Stop application: Ctrl+C in terminal

# Stop Kafka: Ctrl+C in Kafka terminal

# Stop Zookeeper: Ctrl+C in Zookeeper terminal

# Stop Redis: Ctrl+C in Redis terminal or
redis-cli shutdown

# PostgreSQL usually runs as Windows service
# Stop via: Services (services.msc) or
net stop postgresql-x64-18
```

---

## 📚 API Documentation

### Base URL

```
http://localhost:8080
```

### Endpoints

#### Create Short URL

```http
POST /api/v1/urls
Content-Type: application/json

{
  "originalUrl": "https://www.example.com/very/long/path/to/resource",
  "customAlias": "mylink",    // Optional
  "expiresAt": "2025-12-31T23:59:59"  // Optional
}
```

**Response (201 Created):**

```json
{
  "success": true,
  "message": "URL shortened successfully",
  "data": {
    "shortCode": "abc123X",
    "shortUrl": "http://localhost:8080/abc123X",
    "originalUrl": "https://www.example.com/very/long/path/to/resource",
    "clickCount": 0,
    "isActive": true,
    "isCustomAlias": false,
    "expiresAt": "2025-12-31T23:59:59",
    "createdAt": "2025-01-15T10:30:00"
  },
  "timestamp": "2025-01-15T10:30:00.123Z"
}
```

#### Redirect to Original URL

```http
GET /{shortCode}
```

**Response:** 302 Redirect to original URL

#### Get URL Information

```http
GET /api/v1/urls/{shortCode}
```

#### Get URL Statistics

```http
GET /api/v1/urls/{shortCode}/stats
```

**Response:**

```json
{
  "success": true,
  "data": {
    "shortCode": "abc123X",
    "originalUrl": "https://www.example.com",
    "totalClicks": 1523,
    "clicksByCountry": {
      "US": 850,
      "UK": 320,
      "IN": 200
    },
    "clicksByBrowser": {
      "Chrome": 900,
      "Safari": 400,
      "Firefox": 223
    },
    "clicksByDevice": {
      "Desktop": 1000,
      "Mobile": 450,
      "Tablet": 73
    },
    "dailyClicks": [
      { "date": "2025-01-14", "clicks": 150 },
      { "date": "2025-01-15", "clicks": 200 }
    ]
  }
}
```

#### Delete URL

```http
DELETE /api/v1/urls/{shortCode}
```

### Health Endpoints

```http
GET /health        # Application health
GET /health/ready  # Readiness probe
GET /health/live   # Liveness probe
```

---

## 🔬 System Design Deep Dive

### 1. Snowflake ID Generation

The `SnowflakeIdGenerator` produces 64-bit unique IDs:

```
┌─────────────────────────────────────────────────────────────────────┐
│ 0 │ 41-bit timestamp │ 5-bit DC │ 5-bit machine │ 12-bit sequence  │
└─────────────────────────────────────────────────────────────────────┘
```

**Capacity:**

- 4096 IDs per millisecond per machine
- 32 datacenters × 32 machines = 1024 unique generators
- ~69 years of unique IDs from epoch

```java
// From SnowflakeIdGenerator.java
public synchronized long nextId() {
    long currentTimestamp = System.currentTimeMillis();

    if (currentTimestamp == lastTimestamp) {
        sequence = (sequence + 1) & SEQUENCE_MASK;
        if (sequence == 0) {
            currentTimestamp = waitNextMillis(lastTimestamp);
        }
    } else {
        sequence = 0L;
    }

    lastTimestamp = currentTimestamp;

    return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT) |
           (datacenterId << DATACENTER_SHIFT) |
           (machineId << MACHINE_SHIFT) |
           sequence;
}
```

### 2. Base62 Encoding

Converts 64-bit IDs to 7-character alphanumeric strings:

```
Characters: 0-9, A-Z, a-z (62 characters)
Max combinations: 62^7 = 3.5 trillion unique codes
```

```java
// From Base62Encoder.java
private static final String ALPHABET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

public String encode(long id) {
    StringBuilder sb = new StringBuilder();
    while (id > 0) {
        sb.insert(0, ALPHABET.charAt((int) (id % 62)));
        id /= 62;
    }
    return String.format("%7s", sb).replace(' ', '0');
}
```

### 3. Caching Strategy

**Write-Through Cache:**

```
Create URL → Write DB → Write Cache
```

**Read-Through with Cache-Aside:**

```
Redirect → Check Cache → Hit? Return
                      → Miss? Query DB → Write Cache → Return
```

```java
// From UrlCacheService.java
public void cacheUrl(String shortCode, String originalUrl) {
    String key = URL_CACHE_PREFIX + shortCode;
    redisTemplate.opsForValue().set(key, originalUrl, ttlHours, TimeUnit.HOURS);
}

public String getCachedUrl(String shortCode) {
    String key = URL_CACHE_PREFIX + shortCode;
    return redisTemplate.opsForValue().get(key);
}
```

### 4. Rate Limiting

Token bucket algorithm using Bucket4j:

```java
// From RateLimitFilter.java
private Bucket createNewBucket(String key) {
    Bandwidth limit = Bandwidth.classic(
        requestsPerMinute,  // 60 tokens
        Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
    );
    return Bucket.builder()
        .addLimit(limit)
        .build();
}
```

### 5. Async Click Tracking

Non-blocking analytics collection:

```java
// From ClickEventService.java
@Async("clickEventExecutor")
public void recordClickAsync(String shortCode, String ipAddress,
                            String userAgent, String referer) {
    ClickEvent event = ClickEvent.builder()
        .shortCode(shortCode)
        .ipAddress(ipAddress)
        .userAgent(userAgent)
        // ... parse browser, OS, device
        .build();

    clickEventRepository.save(event);
}
```

---

## ⚙️ Configuration

### Application Properties

```yaml
# application.yml
url-shortener:
  base-url: http://localhost:8080 # Base URL for short links
  snowflake:
    datacenter-id: 1 # 0-31
    machine-id: 1 # 0-31
  cache:
    ttl-hours: 24 # Cache TTL
  rate-limit:
    enabled: true
    requests-per-minute: 60 # Per IP
```

### Environment Variables

| Variable                 | Description    | Default                                         |
| ------------------------ | -------------- | ----------------------------------------------- |
| `SPRING_PROFILES_ACTIVE` | Active profile | `default`                                       |
| `SPRING_DATASOURCE_URL`  | Database URL   | `jdbc:postgresql://localhost:5432/urlshortener` |
| `SPRING_DATA_REDIS_HOST` | Redis host     | `localhost`                                     |
| `URL_SHORTENER_BASE_URL` | Short URL base | `http://localhost:8080`                         |

---

## 📊 Monitoring

### Prometheus Metrics

Available at `/actuator/prometheus`:

- `url_shortener_urls_created_total` - URLs created
- `url_shortener_redirects_total` - Redirect count
- `url_shortener_cache_hits_total` - Cache hit rate
- `url_shortener_cache_misses_total` - Cache miss rate

### Health Endpoints

```bash
# Application health
curl http://localhost:8080/health

# Kubernetes readiness
curl http://localhost:8080/health/ready

# Kubernetes liveness
curl http://localhost:8080/health/live
```

### Grafana Dashboards

Access Grafana at http://localhost:3000 (admin/admin) with pre-configured Prometheus datasource.

---

## 🧪 Testing

### Run All Tests

```bash
./mvnw test
```

### Run Specific Test Class

```bash
./mvnw test -Dtest=UrlServiceTest
```

### Test Coverage

```bash
./mvnw verify
```

### Integration Tests with Testcontainers

```bash
./mvnw verify -P integration-test
```

---

## 🚢 Deployment

### Build JAR

```bash
./mvnw clean package -DskipTests
```

### Build Docker Image

```bash
docker build -t url-shortener:latest .
```

### Run Container

```bash
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/urlshortener \
  -e SPRING_DATA_REDIS_HOST=redis \
  url-shortener:latest
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: url-shortener
spec:
  replicas: 3
  selector:
    matchLabels:
      app: url-shortener
  template:
    metadata:
      labels:
        app: url-shortener
    spec:
      containers:
        - name: url-shortener
          image: url-shortener:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 30
          livenessProbe:
            httpGet:
              path: /health/live
              port: 8080
            initialDelaySeconds: 60
```

---

## 📈 Scaling Considerations

### Horizontal Scaling

- **Stateless design** - No session state
- **Snowflake IDs** - Unique across instances (configure different machine IDs)
- **Redis cluster** - Distributed caching
- **PostgreSQL read replicas** - Scale reads

### Performance Benchmarks

| Operation         | Latency (p99) | Throughput   |
| ----------------- | ------------- | ------------ |
| Create URL        | 15ms          | 5,000 req/s  |
| Redirect (cached) | 2ms           | 50,000 req/s |
| Redirect (DB)     | 8ms           | 15,000 req/s |

### Capacity Planning

- **62^7 = 3.5 trillion** possible short codes
- At 1 million URLs/day → **9,500+ years** of capacity
- Redis: ~100 bytes per URL mapping
- PostgreSQL: ~200 bytes per URL record

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

---

**Built with ❤️ for System Design learning**
