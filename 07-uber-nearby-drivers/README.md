# 07. How Uber Finds Nearby Drivers at 1 Million Requests per Second

## System Design: Real-Time Geospatial Matching System

A production-grade implementation of Uber's driver location and matching system, handling millions of location updates and ride requests per second.

---

## 1. Problem Statement

### What We're Building

A real-time geospatial system that:

- Tracks millions of driver locations with sub-second updates
- Finds nearby available drivers within milliseconds
- Matches riders with optimal drivers at massive scale
- Handles 1M+ requests per second with low latency

### Scale Requirements

| Metric              | Target                       |
| ------------------- | ---------------------------- |
| Active drivers      | 5 million                    |
| Location updates    | 5M/sec (1 update/driver/sec) |
| Ride requests       | 1M/sec peak                  |
| Search latency      | < 50ms p99                   |
| Match latency       | < 100ms p99                  |
| Location staleness  | < 3 seconds                  |
| Geographic coverage | Global                       |

### Core Challenges

1. **Write-heavy workload**: 5M location updates/sec
2. **Read-heavy queries**: 1M spatial searches/sec
3. **Real-time requirements**: Sub-second freshness
4. **Geographic distribution**: Global scale with local latency

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              UBER NEARBY DRIVERS                                 │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  ┌─────────────┐     ┌─────────────────────────────────────────────────────┐   │
│  │   Driver    │────▶│                   API Gateway                        │   │
│  │    App      │     │   (Rate Limiting, Auth, Load Balancing)             │   │
│  └─────────────┘     └──────────────┬──────────────────────┬───────────────┘   │
│                                     │                      │                    │
│  ┌─────────────┐                    │                      │                    │
│  │   Rider     │────────────────────┘                      │                    │
│  │    App      │                                           │                    │
│  └─────────────┘                                           │                    │
│                                                            │                    │
│  ┌─────────────────────────────────┐    ┌─────────────────▼─────────────────┐  │
│  │     Location Update Service     │    │      Nearby Search Service        │  │
│  │  ┌───────────────────────────┐  │    │  ┌─────────────────────────────┐  │  │
│  │  │ • Batch location updates  │  │    │  │ • Geohash-based search      │  │  │
│  │  │ • Validate coordinates    │  │    │  │ • K-nearest neighbors       │  │  │
│  │  │ • Update spatial index    │  │    │  │ • Filter by availability    │  │  │
│  │  │ • Publish to Kafka        │  │    │  │ • Distance calculation      │  │  │
│  │  └───────────────────────────┘  │    │  └─────────────────────────────┘  │  │
│  └──────────────┬──────────────────┘    └──────────────┬────────────────────┘  │
│                 │                                      │                        │
│                 ▼                                      ▼                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        Spatial Index Layer                               │   │
│  │  ┌───────────────────┐  ┌───────────────────┐  ┌─────────────────────┐  │   │
│  │  │   Geohash Grid    │  │   QuadTree/S2     │  │   H3 Hexagons       │  │   │
│  │  │   (Fast lookup)   │  │   (Hierarchical)  │  │   (Uber's choice)   │  │   │
│  │  └───────────────────┘  └───────────────────┘  └─────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                     │                                           │
│                 ┌───────────────────┼───────────────────┐                      │
│                 ▼                   ▼                   ▼                      │
│  ┌─────────────────────┐ ┌─────────────────────┐ ┌─────────────────────────┐  │
│  │   Redis Cluster     │ │   PostgreSQL/       │ │   Apache Kafka          │  │
│  │   (Hot data)        │ │   PostGIS           │ │   (Event streaming)     │  │
│  │   • Driver locations│ │   • Historical data │ │   • Location events     │  │
│  │   • Geospatial ops  │ │   • Analytics       │ │   • Match events        │  │
│  └─────────────────────┘ └─────────────────────┘ └─────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Component Overview

| Component        | Responsibility                 | Technology           |
| ---------------- | ------------------------------ | -------------------- |
| API Gateway      | Rate limiting, routing, auth   | Spring Cloud Gateway |
| Location Service | Ingest driver location updates | Spring Boot + Kafka  |
| Nearby Service   | Find drivers within radius     | Spring Boot + Redis  |
| Spatial Index    | Geospatial queries             | Redis GEO + H3       |
| Event Bus        | Async communication            | Apache Kafka         |
| Hot Storage      | Real-time driver data          | Redis Cluster        |
| Cold Storage     | Historical analytics           | PostgreSQL + PostGIS |

---

## 3. Data Model

### Driver Location (Hot Path)

```
Redis Key: driver:location:{driver_id}
Redis GEO: drivers:geo:{city_id}

┌─────────────────────────────────────────────────────────────┐
│                    Driver Location                          │
├─────────────────────────────────────────────────────────────┤
│ driver_id      │ UUID       │ Unique driver identifier      │
│ latitude       │ DOUBLE     │ Current latitude (-90 to 90)  │
│ longitude      │ DOUBLE     │ Current longitude (-180, 180) │
│ geohash        │ VARCHAR(12)│ Encoded location (precision 7)│
│ h3_index       │ BIGINT     │ H3 hexagon index (res 9)      │
│ heading        │ SMALLINT   │ Direction (0-359 degrees)     │
│ speed          │ FLOAT      │ Current speed (m/s)           │
│ accuracy       │ FLOAT      │ GPS accuracy (meters)         │
│ status         │ ENUM       │ AVAILABLE/BUSY/OFFLINE        │
│ vehicle_type   │ ENUM       │ UBERX/UBERXL/BLACK/etc        │
│ timestamp      │ BIGINT     │ Unix millis of update         │
│ city_id        │ VARCHAR    │ Operating city                │
└─────────────────────────────────────────────────────────────┘
```

### Geospatial Indexing Structures

```
1. GEOHASH GRID (Used by Redis GEOADD)
   ┌────┬────┬────┬────┐
   │ 9q │ 9r │ 9x │ 9z │  ← Level 2 (±630km)
   ├────┼────┼────┼────┤
   │ 9p │ 9n │ 9w │ 9y │
   ├────┼────┼────┼────┤     Geohash: "9q8yy" → (37.7749, -122.4194)
   │ 9j │ 9m │ 9t │ 9v │
   ├────┼────┼────┼────┤     Precision vs Coverage:
   │ 9h │ 9k │ 9s │ 9u │     - 4 chars: ±20km
   └────┴────┴────┴────┘     - 6 chars: ±610m
                             - 7 chars: ±76m (Uber uses this)
                             - 8 chars: ±19m

2. H3 HEXAGONAL GRID (Uber's Production System)
      ___     ___     ___
    /     \ /     \ /     \
   /   A   X   B   X   C   \    Resolution 9: ~174m edge
   \       / \     / \     /    Resolution 10: ~66m edge
    \ ___ /   \___ /   \___ /    Resolution 11: ~25m edge
    /     \ /     \ /     \
   /   D   X   E   X   F   \    Benefits:
   \       / \     / \     /    - Uniform cell shape
    \ ___ /   \___ /   \___ /   - No edge distortion
                                - Efficient neighbors
   H3 Index: 0x8928308280fffff

3. QUADTREE (Alternative)
   ┌─────────┬─────────┐
   │    0    │    1    │      Each cell subdivides into 4
   │  ┌──┬──┐│         │      Path: "0132" = NW→NE→SE→NW
   │  │0 │1 ││         │
   │  ├──┼──┤│         │      Good for:
   │  │2 │3 ││         │      - Variable density areas
   │  └──┴──┘│         │      - Dynamic subdivision
   ├─────────┼─────────┤
   │    2    │    3    │
   └─────────┴─────────┘
```

### PostgreSQL Schema (Cold Storage)

```sql
-- Enable PostGIS extension
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS h3;

-- Driver locations history (time-series)
CREATE TABLE driver_location_history (
    id              BIGSERIAL PRIMARY KEY,
    driver_id       UUID NOT NULL,
    location        GEOGRAPHY(POINT, 4326) NOT NULL,
    h3_index        BIGINT NOT NULL,
    geohash         VARCHAR(12) NOT NULL,
    heading         SMALLINT,
    speed           REAL,
    accuracy        REAL,
    status          VARCHAR(20),
    vehicle_type    VARCHAR(20),
    city_id         VARCHAR(50),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (recorded_at);

-- Create partitions (daily)
CREATE TABLE driver_location_history_2024_01
    PARTITION OF driver_location_history
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

-- Spatial index for geographic queries
CREATE INDEX idx_location_geo ON driver_location_history
    USING GIST (location);

-- H3 index for hexagon-based queries
CREATE INDEX idx_location_h3 ON driver_location_history (h3_index);

-- Geohash prefix index for grid queries
CREATE INDEX idx_location_geohash ON driver_location_history
    USING btree (geohash varchar_pattern_ops);

-- Composite index for common query patterns
CREATE INDEX idx_driver_time ON driver_location_history
    (driver_id, recorded_at DESC);

-- Drivers table
CREATE TABLE drivers (
    driver_id       UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(255),
    vehicle_type    VARCHAR(20) NOT NULL,
    vehicle_info    JSONB,
    rating          DECIMAL(3,2) DEFAULT 5.00,
    status          VARCHAR(20) DEFAULT 'OFFLINE',
    city_id         VARCHAR(50),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Ride requests
CREATE TABLE ride_requests (
    request_id      UUID PRIMARY KEY,
    rider_id        UUID NOT NULL,
    pickup_location GEOGRAPHY(POINT, 4326) NOT NULL,
    dropoff_location GEOGRAPHY(POINT, 4326),
    pickup_h3       BIGINT NOT NULL,
    vehicle_type    VARCHAR(20),
    status          VARCHAR(20) DEFAULT 'PENDING',
    matched_driver  UUID REFERENCES drivers(driver_id),
    search_radius_m INTEGER DEFAULT 5000,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    matched_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

-- Spatial index for ride pickups
CREATE INDEX idx_ride_pickup ON ride_requests USING GIST (pickup_location);
```

---

## 4. API Design

### REST Endpoints

```yaml
# Location Update API (Driver App)
POST /api/v1/drivers/{driverId}/location
Request:
  {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "heading": 180,
    "speed": 12.5,
    "accuracy": 10.0,
    "timestamp": 1704067200000
  }
Response: 204 No Content

# Batch Location Update (High throughput)
POST /api/v1/locations/batch
Request:
  {
    "locations": [
      {"driverId": "uuid1", "lat": 37.77, "lng": -122.41, "ts": 1704067200000},
      {"driverId": "uuid2", "lat": 37.78, "lng": -122.42, "ts": 1704067200001}
    ]
  }
Response:
  {
    "processed": 2,
    "failed": 0
  }

# Find Nearby Drivers (Rider App)
GET /api/v1/drivers/nearby?lat={lat}&lng={lng}&radius={meters}&limit={n}&vehicleType={type}
Response:
  {
    "drivers": [
      {
        "driverId": "uuid",
        "latitude": 37.7751,
        "longitude": -122.4183,
        "distance": 150.5,
        "eta": 180,
        "heading": 90,
        "vehicleType": "UBERX",
        "rating": 4.85
      }
    ],
    "searchRadiusUsed": 2000,
    "totalFound": 15
  }

# Request Ride (Triggers matching)
POST /api/v1/rides/request
Request:
  {
    "riderId": "uuid",
    "pickupLocation": {"lat": 37.7749, "lng": -122.4194},
    "dropoffLocation": {"lat": 37.7849, "lng": -122.4094},
    "vehicleType": "UBERX"
  }
Response:
  {
    "requestId": "uuid",
    "status": "MATCHING",
    "estimatedWait": 120
  }

# Driver Status Update
PUT /api/v1/drivers/{driverId}/status
Request:
  {
    "status": "AVAILABLE" | "BUSY" | "OFFLINE"
  }
Response: 200 OK

# Get Driver ETA
GET /api/v1/drivers/{driverId}/eta?toLat={lat}&toLng={lng}
Response:
  {
    "driverId": "uuid",
    "etaSeconds": 240,
    "distanceMeters": 1500,
    "route": [...] // Optional polyline
  }
```

### WebSocket API (Real-time Updates)

```yaml
# Driver location stream (for rider tracking)
WS /ws/v1/rides/{rideId}/driver-location
Message:
  {
    "type": "LOCATION_UPDATE",
    "driverId": "uuid",
    "latitude": 37.7751,
    "longitude": -122.4183,
    "heading": 90,
    "eta": 120,
    "timestamp": 1704067200000
  }

# Nearby drivers stream (for map display)
WS /ws/v1/nearby?lat={lat}&lng={lng}&radius={meters}
Message:
  {
    "type": "DRIVERS_UPDATE",
    "drivers": [...],
    "removedDrivers": ["uuid1", "uuid2"]
  }
```

---

## 5. Core Algorithms

### 5.1 Geohash-Based Nearby Search

```
Algorithm: Find drivers within radius using Geohash

1. ENCODE rider location to geohash at precision P
2. CALCULATE bounding box for search radius
3. FIND all geohash cells that intersect bounding box
4. QUERY Redis for drivers in each cell
5. FILTER by exact distance (Haversine)
6. SORT by distance
7. RETURN top K results

Example: Find drivers within 2km of (37.7749, -122.4194)

Step 1: Encode → "9q8yyk8"
Step 2: Bounding box needs ~3x3 cells at precision 6
Step 3: Neighboring cells:
        ┌────────┬────────┬────────┐
        │ 9q8yyj │ 9q8yym │ 9q8yyq │
        ├────────┼────────┼────────┤
        │ 9q8yyh │ 9q8yyk │ 9q8yyn │ ← Center
        ├────────┼────────┼────────┤
        │ 9q8yy5 │ 9q8yy7 │ 9q8yyp │
        └────────┴────────┴────────┘
Step 4: Redis GEORADIUS on each cell
Step 5: Calculate exact distance, filter > 2km
Step 6: Sort ascending by distance
```

### 5.2 H3-Based Nearby Search (Uber's Approach)

```
Algorithm: K-ring search with H3

1. CONVERT rider location to H3 index at resolution R
2. GET k-ring of hexagons (rings 0 to max_rings)
3. For each ring:
   a. QUERY all drivers in hexagons
   b. If enough drivers found, STOP
4. CALCULATE exact distances
5. SORT and RETURN top K

H3 Resolution Selection:
- Resolution 9:  ~174m edge, ~0.1 km² area (street level)
- Resolution 10: ~66m edge,  ~0.015 km² area (building level)
- Resolution 11: ~25m edge,  ~0.002 km² area (precise)

K-ring expansion:
Ring 0: 1 hexagon (center)
Ring 1: 7 hexagons (center + 6 neighbors)
Ring 2: 19 hexagons
Ring 3: 37 hexagons

Search radius mapping:
- 500m  → Ring 2 at resolution 9
- 1km   → Ring 3 at resolution 9
- 2km   → Ring 6 at resolution 9
- 5km   → Ring 2 at resolution 8
```

### 5.3 Haversine Distance Formula

```
Algorithm: Calculate great-circle distance between two points

Given: Point A (lat1, lon1) and Point B (lat2, lon2)

1. Convert to radians:
   φ1 = lat1 × π/180
   φ2 = lat2 × π/180
   Δφ = (lat2 - lat1) × π/180
   Δλ = (lon2 - lon1) × π/180

2. Calculate Haversine:
   a = sin²(Δφ/2) + cos(φ1) × cos(φ2) × sin²(Δλ/2)
   c = 2 × atan2(√a, √(1-a))

3. Distance:
   d = R × c    (R = 6371km = Earth's radius)

Complexity: O(1)
Accuracy: ~0.5% error due to Earth's ellipsoid shape
```

### 5.4 Driver Matching Algorithm

```
Algorithm: Optimal driver selection

Inputs:
- Rider location (lat, lng)
- Available drivers within radius
- Vehicle type preference
- Rider priority score

Scoring Function:
  score(driver) = w1 × (1/distance)
                + w2 × driver_rating
                + w3 × acceptance_rate
                + w4 × (1/eta)
                - w5 × surge_multiplier

Where:
  w1 = 0.4 (proximity weight)
  w2 = 0.2 (quality weight)
  w3 = 0.2 (reliability weight)
  w4 = 0.15 (ETA weight)
  w5 = 0.05 (surge penalty)

Process:
1. GET nearby drivers (k-ring search)
2. FILTER by vehicle type and status
3. CALCULATE score for each driver
4. SORT by score descending
5. DISPATCH to top driver
6. If rejected, try next driver
7. Repeat until matched or timeout
```

---

## 6. Scaling Strategy

### 6.1 Horizontal Scaling

```
                    ┌─────────────────────────────────────┐
                    │           Load Balancer             │
                    │    (Geographic + Consistent Hash)   │
                    └──────────────┬──────────────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌───────────────┐          ┌───────────────┐          ┌───────────────┐
│  Region: US   │          │  Region: EU   │          │  Region: APAC │
│               │          │               │          │               │
│ ┌───────────┐ │          │ ┌───────────┐ │          │ ┌───────────┐ │
│ │ Location  │ │          │ │ Location  │ │          │ │ Location  │ │
│ │ Service   │ │          │ │ Service   │ │          │ │ Service   │ │
│ │ (10 pods) │ │          │ │ (8 pods)  │ │          │ │ (12 pods) │ │
│ └───────────┘ │          │ └───────────┘ │          │ └───────────┘ │
│               │          │               │          │               │
│ ┌───────────┐ │          │ ┌───────────┐ │          │ ┌───────────┐ │
│ │ Nearby    │ │          │ │ Nearby    │ │          │ │ Nearby    │ │
│ │ Service   │ │          │ │ Service   │ │          │ │ Service   │ │
│ │ (15 pods) │ │          │ │ (10 pods) │ │          │ │ (20 pods) │ │
│ └───────────┘ │          │ └───────────┘ │          │ └───────────┘ │
│               │          │               │          │               │
│ ┌───────────┐ │          │ ┌───────────┐ │          │ ┌───────────┐ │
│ │  Redis    │ │          │ │  Redis    │ │          │ │  Redis    │ │
│ │  Cluster  │ │          │ │  Cluster  │ │          │ │  Cluster  │ │
│ └───────────┘ │          │ └───────────┘ │          │ └───────────┘ │
└───────────────┘          └───────────────┘          └───────────────┘
```

### 6.2 Sharding Strategy

```
Geographic Sharding:
- Shard by city/region
- Each city has dedicated Redis cluster
- Cross-city queries rare (riders don't cross regions)

City-Based Sharding:
┌─────────────────────────────────────────────────────┐
│                    City Router                       │
│   city_id = h3_to_city(h3_index)                    │
│   shard = city_to_shard(city_id)                    │
└───────────────────────┬─────────────────────────────┘
                        │
    ┌───────────────────┼───────────────────┐
    ▼                   ▼                   ▼
┌─────────┐       ┌─────────┐       ┌─────────┐
│ Shard 1 │       │ Shard 2 │       │ Shard 3 │
│ NYC     │       │ LA/SF   │       │ Chicago │
│ Boston  │       │ Seattle │       │ Denver  │
└─────────┘       └─────────┘       └─────────┘

H3-Based Sharding (for global scale):
- Use H3 resolution 4 (avg edge ~22km)
- Map each H3 cell to a shard
- ~3,000 cells globally at res 4
- Consistent hashing: shard = hash(h3_res4) % num_shards
```

### 6.3 Caching Strategy

```
Multi-Level Cache:

┌─────────────────────────────────────────────────────────────┐
│ L1: Application Cache (Caffeine)                            │
│ - Driver metadata                                           │
│ - City configurations                                       │
│ - TTL: 60 seconds                                           │
│ - Size: 100MB per instance                                  │
└─────────────────────────┬───────────────────────────────────┘
                          │ Miss
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ L2: Redis Cluster                                           │
│ - Real-time driver locations (GEO)                         │
│ - Driver status                                             │
│ - TTL: 30 seconds (auto-expire stale locations)            │
└─────────────────────────┬───────────────────────────────────┘
                          │ Miss (rare for locations)
                          ▼
┌─────────────────────────────────────────────────────────────┐
│ L3: PostgreSQL + PostGIS                                    │
│ - Historical locations                                      │
│ - Driver profiles                                           │
│ - Analytics data                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 7. Consistency & Fault Tolerance

### 7.1 Location Update Flow

```
Eventual Consistency Model:

Driver App                Location Service              Redis              Kafka
    │                           │                         │                  │
    │──POST /location──────────▶│                         │                  │
    │                           │                         │                  │
    │                           │──GEOADD + SET──────────▶│                  │
    │                           │                         │                  │
    │◀─────────204 No Content───│                         │                  │
    │                           │                         │                  │
    │                           │──PUBLISH location.updated──────────────────▶│
    │                           │                         │                  │
    │                           │                         │     (Async)      │
    │                           │                         │                  │
                                                          │◀─CONSUME─────────│
                                                          │  (Analytics)     │

Guarantees:
- Location visible in Redis within 10ms
- At-most-once delivery to Redis (OK to lose occasional update)
- At-least-once delivery to Kafka (for analytics)
- Stale data automatically expires (30s TTL)
```

### 7.2 Failure Handling

```
Redis Cluster Failure:
┌──────────────────────────────────────────────────────────────┐
│  Primary Redis ────(fails)────▶ Sentinel detects            │
│       │                              │                       │
│       │                              ▼                       │
│  Replica Redis ◀──────────── Promoted to Primary            │
│       │                              │                       │
│       │                              ▼                       │
│  Application ◀───────────── Reconnects automatically        │
│                                                              │
│  Failover time: < 30 seconds                                 │
│  Data loss: ~30 seconds of locations (acceptable)           │
└──────────────────────────────────────────────────────────────┘

Circuit Breaker Pattern:
┌─────────────────────────────────────────────────────────────┐
│  Location Service                                           │
│       │                                                     │
│       ▼                                                     │
│  ┌──────────────┐                                          │
│  │   Circuit    │──CLOSED──▶ Normal operation              │
│  │   Breaker    │                                          │
│  └──────────────┘                                          │
│       │                                                     │
│       │ (5 failures in 10s)                                │
│       ▼                                                     │
│  ┌──────────────┐                                          │
│  │    OPEN      │──────────▶ Reject requests, return cache │
│  └──────────────┘                                          │
│       │                                                     │
│       │ (30s timeout)                                       │
│       ▼                                                     │
│  ┌──────────────┐                                          │
│  │  HALF-OPEN   │──────────▶ Allow 1 request to test       │
│  └──────────────┘                                          │
└─────────────────────────────────────────────────────────────┘
```

### 7.3 Data Consistency

```
Location Staleness Handling:
1. Each location has timestamp
2. Query filters: WHERE timestamp > NOW() - 30s
3. TTL in Redis: 30 seconds auto-expire
4. Client displays "Last seen X minutes ago" for stale

Conflict Resolution:
- Last-write-wins for location updates
- Timestamp from client (validated by server)
- Server rejects updates with old timestamps

Idempotency:
- Location updates are naturally idempotent
- Same location sent twice = no problem
- Driver ID + timestamp = unique key
```

---

## 8. Real-Time Communication

### 8.1 Location Streaming Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Real-Time Location Streaming                       │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  Driver App                                                           │
│      │                                                                │
│      │ POST /location (every 4s when moving)                         │
│      ▼                                                                │
│  ┌────────────────┐     ┌─────────────┐     ┌──────────────────┐    │
│  │ Location       │────▶│   Kafka     │────▶│ Location         │    │
│  │ Service        │     │ (partition  │     │ Consumer         │    │
│  │                │     │  by city)   │     │ Service          │    │
│  └────────────────┘     └─────────────┘     └────────┬─────────┘    │
│                                                       │              │
│                              ┌────────────────────────┘              │
│                              │                                        │
│                              ▼                                        │
│                    ┌─────────────────┐                               │
│                    │   WebSocket     │                               │
│                    │   Gateway       │                               │
│                    │   (Gorilla/     │                               │
│                    │    Netty)       │                               │
│                    └────────┬────────┘                               │
│                             │                                        │
│            ┌────────────────┼────────────────┐                       │
│            ▼                ▼                ▼                       │
│     ┌───────────┐    ┌───────────┐    ┌───────────┐                 │
│     │ Rider App │    │ Rider App │    │ Rider App │                 │
│     │ (tracking │    │ (nearby   │    │ (ETA      │                 │
│     │  driver)  │    │  drivers) │    │  updates) │                 │
│     └───────────┘    └───────────┘    └───────────┘                 │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘

Scaling WebSockets:
- Each gateway handles ~100K concurrent connections
- Sticky sessions by user_id
- Pub/Sub for cross-gateway messages
- Heartbeat every 30s to detect dead connections
```

### 8.2 Message Protocol

```json
// Driver → Server (Location Update)
{
  "type": "LOCATION_UPDATE",
  "payload": {
    "lat": 37.7749,
    "lng": -122.4194,
    "heading": 180,
    "speed": 12.5,
    "accuracy": 10,
    "ts": 1704067200000
  }
}

// Server → Rider (Driver Location)
{
  "type": "DRIVER_LOCATION",
  "payload": {
    "driverId": "uuid",
    "lat": 37.7751,
    "lng": -122.4183,
    "heading": 90,
    "eta": 120,
    "distanceRemaining": 500
  }
}

// Server → Rider (Nearby Drivers)
{
  "type": "NEARBY_UPDATE",
  "payload": {
    "drivers": [
      {"id": "uuid1", "lat": 37.77, "lng": -122.41, "type": "UBERX"},
      {"id": "uuid2", "lat": 37.78, "lng": -122.42, "type": "UBERXL"}
    ],
    "removed": ["uuid3", "uuid4"]
  }
}
```

---

## 9. Security

### 9.1 Authentication & Authorization

```
┌─────────────────────────────────────────────────────────────┐
│                    Security Architecture                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Driver/Rider App                                            │
│       │                                                      │
│       │ JWT Token (short-lived, 15 min)                     │
│       │ + Device fingerprint                                 │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │ API Gateway  │ ─── Validate JWT                          │
│  │              │ ─── Rate limit by user                    │
│  │              │ ─── Check device binding                   │
│  └──────────────┘                                           │
│       │                                                      │
│       │ Internal service token                               │
│       ▼                                                      │
│  ┌──────────────┐                                           │
│  │ Location     │ ─── Verify driver owns this driver_id     │
│  │ Service      │ ─── Validate coordinates are reasonable   │
│  └──────────────┘     (not moving faster than 200 km/h)     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 Location Privacy

```
Privacy Measures:
1. Fuzzing: Add ±50m random offset for nearby display
2. Precision: Return 5 decimal places max (11m precision)
3. Access control: Only matched rider sees exact driver location
4. Data retention: Location history purged after 30 days
5. Anonymization: Analytics use aggregated/anonymized data

Rate Limiting:
- Location updates: 1 per second per driver
- Nearby queries: 10 per second per rider
- Burst allowance: 2x normal rate for 5 seconds
```

---

## 10. Observability

### 10.1 Key Metrics

```yaml
# Location Service Metrics
location_updates_total:
  type: Counter
  labels: [city_id, status]
  description: Total location updates received

location_update_latency_ms:
  type: Histogram
  buckets: [1, 5, 10, 25, 50, 100, 250, 500]
  description: Time to process location update

# Nearby Service Metrics
nearby_search_total:
  type: Counter
  labels: [city_id, vehicle_type, result_count_bucket]
  description: Total nearby searches

nearby_search_latency_ms:
  type: Histogram
  buckets: [5, 10, 25, 50, 100, 250, 500, 1000]
  description: Time to find nearby drivers

drivers_found_per_search:
  type: Histogram
  buckets: [0, 1, 5, 10, 25, 50, 100]
  description: Number of drivers found per search

# System Health
active_drivers:
  type: Gauge
  labels: [city_id, status]
  description: Currently active drivers

redis_operations_total:
  type: Counter
  labels: [operation, status]
  description: Redis operation count

redis_latency_ms:
  type: Histogram
  description: Redis operation latency
```

### 10.2 Distributed Tracing

```
Trace: Nearby Driver Search
────────────────────────────────────────────────────────────────

[API Gateway] ──────────────────────────────────────────── 45ms
    │
    └──[Auth Validation] ────────────────────────────────── 5ms
    │
    └──[Nearby Service] ────────────────────────────────── 35ms
           │
           └──[H3 Index Calculation] ─────────────────────  1ms
           │
           └──[Redis GEORADIUS] ──────────────────────────  8ms
           │
           └──[Distance Calculation] ─────────────────────  3ms
           │
           └──[Driver Filtering] ─────────────────────────  2ms
           │
           └──[Response Serialization] ───────────────────  1ms

Total: 45ms (Target: <50ms ✓)
```

---

## 11. Trade-offs & Decisions

### 11.1 Geohash vs H3 vs S2

| Aspect           | Geohash       | H3          | S2          |
| ---------------- | ------------- | ----------- | ----------- |
| Cell Shape       | Rectangle     | Hexagon     | Square      |
| Edge Distortion  | High at poles | Minimal     | Moderate    |
| Neighbor Finding | 8 neighbors   | 6 neighbors | 8 neighbors |
| Implementation   | Simple        | Moderate    | Complex     |
| Redis Support    | Native GEOADD | Custom      | Custom      |
| Uber's Choice    | No            | **Yes**     | No          |

**Decision**: Use Redis GEO (Geohash) for simplicity, with H3 overlay for advanced queries.

### 11.2 Redis vs Custom In-Memory Store

| Aspect            | Redis    | Custom Store   |
| ----------------- | -------- | -------------- |
| Development Time  | Low      | High           |
| GEO Support       | Built-in | Must implement |
| Clustering        | Built-in | Must implement |
| Persistence       | RDB/AOF  | Must implement |
| Latency           | <1ms     | <0.5ms         |
| Memory Efficiency | Good     | Can be better  |

**Decision**: Use Redis for faster development, acceptable performance.

### 11.3 Update Frequency Trade-offs

| Frequency  | Bandwidth | Battery    | Accuracy   | Server Load |
| ---------- | --------- | ---------- | ---------- | ----------- |
| 1 second   | High      | High drain | Excellent  | 5M/sec      |
| 4 seconds  | Medium    | Moderate   | Good       | 1.25M/sec   |
| 10 seconds | Low       | Low drain  | Acceptable | 500K/sec    |

**Decision**: 4 seconds when moving, 30 seconds when stationary.

---

## 12. Interview Discussion Points

### Frequently Asked Questions

**Q: How do you handle 5M location updates per second?**

```
A: Multi-layer approach:
1. Batch updates at client (send every 4s, not every GPS tick)
2. Kafka as buffer (absorbs spikes, smooths load)
3. Redis cluster with geographic sharding (5M/s spread across 50 nodes = 100K/node)
4. Pipeline Redis commands (batch writes)
5. Async processing (don't wait for Kafka ack)
```

**Q: Why not use a traditional database for location storage?**

```
A: Write amplification problem:
- PostgreSQL: Each update = WAL write + index update + table update
- 5M writes/sec would need ~500 database nodes
- Redis: In-memory, single-threaded, O(log N) for GEO operations
- Can handle 100K+ operations/sec per node
```

**Q: How do you ensure location freshness?**

```
A: TTL-based expiration:
1. Each location has 30-second TTL in Redis
2. Drivers send updates every 4 seconds (7 chances before expiry)
3. Stale drivers automatically removed from search results
4. Client shows "last seen" for partially stale data
```

**Q: How would you optimize for dense urban areas?**

```
A: Dynamic precision adjustment:
1. Use higher H3 resolution in dense areas (res 10 vs res 9)
2. Smaller search radius (500m vs 2km)
3. More aggressive filtering (top 5 vs top 20)
4. Pre-computed demand heatmaps for surge pricing
```

**Q: How do you handle cross-region trips?**

```
A: Edge cases:
1. Most trips are intra-city (99%+)
2. Cross-region: Query both shards, merge results
3. Airport trips: Special handling with dedicated queues
4. Border cities: Assigned to single shard (no split)
```

### Key System Design Principles Demonstrated

1. **Geographic Sharding**: Natural partitioning for location data
2. **Eventual Consistency**: Acceptable for location updates
3. **Write-Optimized Storage**: Redis over traditional DB
4. **Hierarchical Spatial Index**: H3/Geohash for efficient queries
5. **Circuit Breaker**: Graceful degradation under failure
6. **Multi-Level Caching**: L1 (app) + L2 (Redis) + L3 (DB)
7. **Event Sourcing**: Kafka for audit and analytics

---

## Tech Stack Summary

| Component     | Technology           | Purpose                     |
| ------------- | -------------------- | --------------------------- |
| API Layer     | Spring Boot 3.2      | REST + WebSocket endpoints  |
| Spatial Index | Redis GEO + H3       | Real-time location queries  |
| Hot Storage   | Redis Cluster        | Driver locations, status    |
| Cold Storage  | PostgreSQL + PostGIS | Historical data, analytics  |
| Message Queue | Apache Kafka         | Event streaming, decoupling |
| Caching       | Caffeine + Redis     | Multi-level caching         |
| Monitoring    | Prometheus + Grafana | Metrics and dashboards      |
| Tracing       | Jaeger               | Distributed tracing         |

---

## File Structure

```
07-uber-nearby-drivers/
├── src/main/java/com/uber/
│   ├── UberNearbyApplication.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── KafkaConfig.java
│   │   └── WebSocketConfig.java
│   ├── entity/
│   │   ├── Driver.java
│   │   ├── DriverLocation.java
│   │   └── RideRequest.java
│   ├── dto/
│   │   ├── LocationUpdateRequest.java
│   │   ├── NearbyDriversResponse.java
│   │   └── RideRequestDto.java
│   ├── repository/
│   │   ├── DriverRepository.java
│   │   └── RideRequestRepository.java
│   ├── service/
│   │   ├── LocationService.java
│   │   ├── NearbySearchService.java
│   │   ├── MatchingService.java
│   │   └── GeoHashService.java
│   ├── spatial/
│   │   ├── GeoHash.java
│   │   ├── H3Index.java
│   │   └── HaversineDistance.java
│   ├── controller/
│   │   ├── LocationController.java
│   │   ├── NearbyController.java
│   │   └── RideController.java
│   └── websocket/
│       └── LocationWebSocketHandler.java
├── src/main/resources/
│   ├── application.yml
│   └── schema.sql
├── docker-compose.yml
├── Dockerfile
└── README.md
```
