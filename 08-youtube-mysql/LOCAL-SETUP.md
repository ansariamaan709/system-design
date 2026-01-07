# YouTube MySQL Scaling - Local Setup Guide

## Quick Start

### 1. Start Infrastructure

```powershell
.\start-services.ps1
```

This starts:

- **MySQL 8.0** on port 3306
- **Redis 7** on port 6379
- **Kafka + Zookeeper** on ports 9092, 2181
- **Kafka UI** at http://localhost:8081
- **Redis Commander** at http://localhost:8082
- **phpMyAdmin** at http://localhost:8083
- **Prometheus** at http://localhost:9090
- **Grafana** at http://localhost:3000

### 2. Setup Database

```powershell
.\setup-database.ps1
```

### 3. Run Application

```powershell
.\run-local.ps1
```

Application runs at: http://localhost:8080

---

## Prerequisites

- **Java 21+** (OpenJDK or Eclipse Temurin)
- **Docker Desktop** for Windows
- **Maven 3.9+** (or use included `mvnw.cmd`)

### Verify Prerequisites

```powershell
java -version        # Should show Java 21+
docker --version     # Should show Docker 24+
mvn -version         # Should show Maven 3.9+
```

---

## Service URLs

| Service         | URL                                   | Credentials           |
| --------------- | ------------------------------------- | --------------------- |
| Application     | http://localhost:8080                 | -                     |
| Swagger UI      | http://localhost:8080/swagger-ui.html | -                     |
| Actuator        | http://localhost:8080/actuator        | -                     |
| MySQL           | localhost:3306                        | root / youtube_secret |
| Redis           | localhost:6379                        | -                     |
| Kafka           | localhost:9092                        | -                     |
| Kafka UI        | http://localhost:8081                 | -                     |
| Redis Commander | http://localhost:8082                 | -                     |
| phpMyAdmin      | http://localhost:8083                 | root / youtube_secret |
| Prometheus      | http://localhost:9090                 | -                     |
| Grafana         | http://localhost:3000                 | admin / admin         |

---

## API Testing

### Upload Video

```powershell
$body = @{
    channelId = 1
    title = "Test Video"
    description = "Testing YouTube MySQL scaling"
    visibility = "PUBLIC"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/videos" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

### Get Video

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/videos/1"
```

### Record View

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/videos/1/view?userId=1" -Method POST
```

### Create Channel

```powershell
$body = @{
    userId = 1
    handle = "@testchannel"
    displayName = "Test Channel"
    description = "Testing channels"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/channels" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

### Subscribe to Channel

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/channels/1/subscribe?userId=2" -Method POST
```

### Add Comment

```powershell
$body = @{
    videoId = 1
    userId = 1
    content = "Great video!"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/comments" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

### Like Video

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/videos/1/like?userId=1" -Method POST
```

---

## Architecture Overview

### Vitess-Style Sharding Simulation

```
┌─────────────────────────────────────────────────────────────┐
│                      Application                             │
│                                                              │
│  ┌─────────────────┐    ┌───────────────────────────────┐   │
│  │  ShardRouter    │    │   ConsistencyManager          │   │
│  │  (VTGate sim)   │    │   (Read-your-writes)         │   │
│  └────────┬────────┘    └───────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼────────────────────────────────────────────┐    │
│  │                    MySQL (VTTablet sim)              │    │
│  │                                                       │    │
│  │  videos_shard_0   │  users_shard_0  │  social_shard_0│    │
│  │  videos_shard_1   │  users_shard_1  │  social_shard_1│    │
│  │  ...              │  ...            │  ...           │    │
│  │  videos_shard_255 │  users_shard_127│  social_shard_511   │
│  └───────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### View Count Flow

```
User View → Kafka → Consumer → Redis (Buffer) → MySQL (Batch Write)
                                    │
                                    ▼
                            Increment every 1000
                            Flush every 5 minutes
```

### ID Generation

```
64-bit Snowflake ID:
┌────────────────────────────────────────────────────────────────┐
│ 0 │ 41-bit timestamp │ 5-bit DC │ 5-bit worker │ 12-bit seq   │
└────────────────────────────────────────────────────────────────┘
    │                   │          │              │
    │                   │          │              └─ 4096 IDs/ms
    │                   │          └─ 32 workers per DC
    │                   └─ 32 datacenters
    └─ 69 years from epoch
```

---

## Troubleshooting

### Docker Issues

**Containers not starting:**

```powershell
docker-compose down -v
docker-compose up -d
```

**Port conflicts:**

```powershell
netstat -ano | findstr :3306
netstat -ano | findstr :6379
netstat -ano | findstr :9092
```

### MySQL Connection Issues

**Check MySQL is ready:**

```powershell
docker exec youtube-mysql mysql -uroot -pyoutube_secret -e "SELECT 1"
```

**View logs:**

```powershell
docker logs youtube-mysql
```

### Kafka Issues

**Check Kafka broker:**

```powershell
docker exec youtube-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

**List topics:**

```powershell
docker exec youtube-kafka kafka-topics --list --bootstrap-server localhost:9092
```

### Application Issues

**Check application logs:**

```powershell
Get-Content -Path "logs/youtube.log" -Tail 100 -Wait
```

**Health check:**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

---

## Development

### Run Tests

```powershell
.\mvnw.cmd test
```

### Build JAR

```powershell
.\mvnw.cmd clean package -DskipTests
```

### Run with Debug

```powershell
$env:JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
```

### Hot Reload (DevTools)

Spring DevTools is included - the application automatically restarts when files change.

---

## Database Schema

### Core Tables

| Table         | Purpose               | Sharding Key |
| ------------- | --------------------- | ------------ |
| videos        | Video metadata        | video_id     |
| video_stats   | View counts, likes    | video_id     |
| video_formats | Available qualities   | video_id     |
| users         | User accounts         | user_id      |
| channels      | Creator channels      | channel_id   |
| subscriptions | Channel subscriptions | channel_id   |
| comments      | Video comments        | video_id     |
| video_likes   | Like/dislike records  | video_id     |
| watch_history | User viewing history  | user_id      |

### Key Indexes

```sql
-- Video lookup (point query)
CREATE INDEX idx_video_channel ON videos(channel_id);

-- User subscriptions (scatter query)
CREATE INDEX idx_sub_subscriber ON subscriptions(subscriber_id);

-- Comments by video (range query)
CREATE INDEX idx_comment_video_created ON comments(video_id, created_at DESC);
```

---

## Monitoring

### Key Metrics

| Metric                        | Description           |
| ----------------------------- | --------------------- |
| `video.views`                 | View event counter    |
| `video.cache.hit`             | Cache hit ratio       |
| `shard.route.time`            | Shard routing latency |
| `hikaricp.connections.active` | Active DB connections |
| `kafka.consumer.lag`          | Consumer lag          |

### Grafana Dashboards

Import the included dashboard:

1. Open http://localhost:3000
2. Login (admin/admin)
3. Import JSON from `grafana-dashboard.json`

---

## Cleanup

### Stop All Services

```powershell
.\stop-services.ps1
```

### Remove All Data

```powershell
docker-compose down -v
```

### Reset Everything

```powershell
docker-compose down -v
docker system prune -af
```
