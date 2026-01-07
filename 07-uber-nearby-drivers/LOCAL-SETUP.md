# Uber Nearby Drivers - Local Setup Guide

## How Uber Finds Nearby Drivers at 1 Million Requests per Second

This project implements a real-time geospatial system for finding nearby drivers at massive scale.

---

## Prerequisites

- **Java 21** or later
- **Docker Desktop** (for PostgreSQL, Redis, Kafka)
- **Maven 3.9+** (or use included wrapper)
- **8GB+ RAM** recommended for all services

---

## Quick Start

### 1. Start Infrastructure Services

```powershell
# From project root
.\start-services.ps1
```

This starts:

- **PostgreSQL 15** with PostGIS (port 5432)
- **Redis 7** (port 6379)
- **Apache Kafka** (port 9092)
- **Kafka UI** (port 8081)
- **Redis Commander** (port 8082)
- **Prometheus** (port 9090)
- **Grafana** (port 3000)

### 2. Run the Application

```powershell
.\run-local.ps1
```

Or manually:

```powershell
.\mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### 3. Verify Installation

Application health check:

```powershell
curl http://localhost:8080/actuator/health
```

---

## API Endpoints

### Location Update (Driver App)

```bash
# Update driver location
POST /api/v1/drivers/{driverId}/location
Header: X-City-Id: san_francisco
{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "heading": 180,
  "speed": 12.5,
  "accuracy": 10.0
}
```

### Find Nearby Drivers (Rider App)

```bash
# Find drivers within 2km
GET /api/v1/drivers/nearby?lat=37.7749&lng=-122.4194&radius=2000&limit=10
Header: X-City-Id: san_francisco
```

### Request a Ride

```bash
POST /api/v1/rides/request
Header: X-City-Id: san_francisco
{
  "riderId": "uuid",
  "pickupLocation": {"lat": 37.7749, "lng": -122.4194},
  "dropoffLocation": {"lat": 37.7849, "lng": -122.4094},
  "vehicleType": "UBERX"
}
```

### Update Driver Status

```bash
PUT /api/v1/drivers/{driverId}/status
{
  "status": "AVAILABLE"
}
```

---

## Testing

### Run Unit Tests

```powershell
.\mvnw test
```

### Test with Sample Data

```powershell
# Create a test driver
$driverId = [guid]::NewGuid().ToString()

# Update location
Invoke-RestMethod -Method POST `
    -Uri "http://localhost:8080/api/v1/drivers/$driverId/location" `
    -Headers @{"X-City-Id"="san_francisco"; "Content-Type"="application/json"} `
    -Body '{"latitude": 37.7749, "longitude": -122.4194, "heading": 90}'

# Find nearby drivers
Invoke-RestMethod -Method GET `
    -Uri "http://localhost:8080/api/v1/drivers/nearby?lat=37.7749&lng=-122.4194&radius=5000" `
    -Headers @{"X-City-Id"="san_francisco"}
```

---

## Service URLs

| Service         | URL                                   | Credentials |
| --------------- | ------------------------------------- | ----------- |
| Application     | http://localhost:8080                 | -           |
| Swagger UI      | http://localhost:8080/swagger-ui.html | -           |
| Actuator        | http://localhost:8080/actuator        | -           |
| Kafka UI        | http://localhost:8081                 | -           |
| Redis Commander | http://localhost:8082                 | -           |
| Prometheus      | http://localhost:9090                 | -           |
| Grafana         | http://localhost:3000                 | admin/admin |

---

## Redis Data Model

### GEO Index (for GEORADIUS queries)

```
Key: drivers:geo:{cityId}
Type: Sorted Set with GEO encoding
Members: driver UUIDs with lat/lng
```

### Driver Location Details

```
Key: driver:location:{driverId}
Type: Hash
Fields: latitude, longitude, geohash, h3Index, heading, speed, accuracy, timestamp
TTL: 30 seconds
```

### Driver Status

```
Key: driver:status:{driverId}
Type: String
Value: AVAILABLE | BUSY | OFFLINE
```

---

## Kafka Topics

| Topic            | Partitions | Purpose                |
| ---------------- | ---------- | ---------------------- |
| location.updates | 32         | Driver location events |
| ride.requests    | 16         | New ride requests      |
| ride.matches     | 16         | Successful matches     |
| driver.status    | 8          | Status changes         |

---

## Key Metrics (Prometheus)

- `location_updates_total` - Total location updates processed
- `location_update_latency_ms` - Location update processing time
- `nearby_search_total` - Total nearby driver searches
- `nearby_search_latency_ms` - Search processing time
- `matching_attempts_total` - Driver matching attempts
- `matching_success_total` - Successful matches

---

## Architecture Overview

```
Driver App → API Gateway → Location Service → Redis GEO
                                           ↘ Kafka → PostgreSQL (async)

Rider App → API Gateway → Nearby Service → Redis GEO
                        ↘ Matching Service → Driver Scoring → Dispatch
```

---

## Troubleshooting

### Redis Connection Failed

```powershell
# Check if Redis is running
docker ps | findstr redis

# Restart Redis
docker-compose restart redis
```

### Kafka Not Starting

```powershell
# Check Zookeeper logs
docker logs uber-zookeeper

# Check Kafka logs
docker logs uber-kafka
```

### Database Connection Issues

```powershell
# Check PostgreSQL
docker exec uber-postgres pg_isready -U postgres

# Connect manually
docker exec -it uber-postgres psql -U postgres -d uber_nearby
```

---

## Performance Tuning

### Redis

- Enable cluster mode for >500K concurrent drivers
- Tune maxmemory-policy for your workload

### Kafka

- Increase partitions for higher throughput
- Enable compression (lz4) for location updates

### Application

- Tune thread pool sizes based on load
- Enable async processing for non-critical paths

---

## Stopping Services

```powershell
.\stop-services.ps1
```

To remove all data:

```powershell
docker-compose down -v
```
