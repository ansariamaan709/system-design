-- Uber Nearby Drivers Schema
-- PostgreSQL with PostGIS extension

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Drivers table
CREATE TABLE IF NOT EXISTS drivers (
    driver_id       UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(255) UNIQUE,
    vehicle_type    VARCHAR(20) NOT NULL,
    vehicle_info    JSONB,
    license_plate   VARCHAR(20),
    rating          DECIMAL(3,2) DEFAULT 5.00,
    total_trips     INTEGER DEFAULT 0,
    acceptance_rate DECIMAL(5,4) DEFAULT 1.0000,
    status          VARCHAR(20) DEFAULT 'OFFLINE',
    city_id         VARCHAR(50),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Index for driver lookups
CREATE INDEX IF NOT EXISTS idx_drivers_status ON drivers(status);
CREATE INDEX IF NOT EXISTS idx_drivers_city ON drivers(city_id);
CREATE INDEX IF NOT EXISTS idx_drivers_vehicle_type ON drivers(vehicle_type);

-- Driver locations table (current location snapshot)
CREATE TABLE IF NOT EXISTS driver_locations (
    driver_id       UUID PRIMARY KEY REFERENCES drivers(driver_id),
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    location        GEOGRAPHY(POINT, 4326),
    geohash         VARCHAR(12) NOT NULL,
    h3_index        BIGINT NOT NULL,
    heading         SMALLINT,
    speed           REAL,
    accuracy        REAL,
    status          VARCHAR(20) DEFAULT 'AVAILABLE',
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Spatial index for geographic queries
CREATE INDEX IF NOT EXISTS idx_driver_locations_geo ON driver_locations USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_driver_locations_h3 ON driver_locations (h3_index);
CREATE INDEX IF NOT EXISTS idx_driver_locations_geohash ON driver_locations USING btree (geohash varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS idx_driver_locations_status ON driver_locations (status);

-- Driver location history (partitioned by time)
CREATE TABLE IF NOT EXISTS driver_location_history (
    id              BIGSERIAL,
    driver_id       UUID NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,
    location        GEOGRAPHY(POINT, 4326),
    geohash         VARCHAR(12) NOT NULL,
    h3_index        BIGINT NOT NULL,
    heading         SMALLINT,
    speed           REAL,
    accuracy        REAL,
    status          VARCHAR(20),
    recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

-- Create monthly partitions (example)
CREATE TABLE IF NOT EXISTS driver_location_history_2024_01 
    PARTITION OF driver_location_history
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE IF NOT EXISTS driver_location_history_2024_02 
    PARTITION OF driver_location_history
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

CREATE TABLE IF NOT EXISTS driver_location_history_2024_03 
    PARTITION OF driver_location_history
    FOR VALUES FROM ('2024-03-01') TO ('2024-04-01');

CREATE TABLE IF NOT EXISTS driver_location_history_default 
    PARTITION OF driver_location_history
    DEFAULT;

-- Riders table
CREATE TABLE IF NOT EXISTS riders (
    rider_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(255) UNIQUE,
    rating          DECIMAL(3,2) DEFAULT 5.00,
    total_trips     INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- Ride requests table
CREATE TABLE IF NOT EXISTS ride_requests (
    request_id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    rider_id            UUID NOT NULL REFERENCES riders(rider_id),
    pickup_latitude     DOUBLE PRECISION NOT NULL,
    pickup_longitude    DOUBLE PRECISION NOT NULL,
    pickup_location     GEOGRAPHY(POINT, 4326),
    pickup_h3           BIGINT NOT NULL,
    pickup_address      TEXT,
    dropoff_latitude    DOUBLE PRECISION,
    dropoff_longitude   DOUBLE PRECISION,
    dropoff_location    GEOGRAPHY(POINT, 4326),
    dropoff_address     TEXT,
    vehicle_type        VARCHAR(20) DEFAULT 'UBERX',
    status              VARCHAR(20) DEFAULT 'PENDING',
    matched_driver_id   UUID REFERENCES drivers(driver_id),
    search_radius_m     INTEGER DEFAULT 5000,
    surge_multiplier    DECIMAL(4,2) DEFAULT 1.00,
    estimated_fare      DECIMAL(10,2),
    actual_fare         DECIMAL(10,2),
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    matched_at          TIMESTAMPTZ,
    pickup_at           TIMESTAMPTZ,
    dropoff_at          TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_reason       TEXT
);

-- Indexes for ride requests
CREATE INDEX IF NOT EXISTS idx_ride_requests_rider ON ride_requests(rider_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_driver ON ride_requests(matched_driver_id);
CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON ride_requests(status);
CREATE INDEX IF NOT EXISTS idx_ride_requests_pickup_geo ON ride_requests USING GIST (pickup_location);
CREATE INDEX IF NOT EXISTS idx_ride_requests_pickup_h3 ON ride_requests(pickup_h3);
CREATE INDEX IF NOT EXISTS idx_ride_requests_created ON ride_requests(created_at DESC);

-- Driver dispatch offers (tracking match attempts)
CREATE TABLE IF NOT EXISTS dispatch_offers (
    offer_id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    request_id      UUID NOT NULL REFERENCES ride_requests(request_id),
    driver_id       UUID NOT NULL REFERENCES drivers(driver_id),
    distance_m      DOUBLE PRECISION NOT NULL,
    eta_seconds     INTEGER NOT NULL,
    score           DOUBLE PRECISION NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    offered_at      TIMESTAMPTZ DEFAULT NOW(),
    responded_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dispatch_offers_request ON dispatch_offers(request_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_offers_driver ON dispatch_offers(driver_id);
CREATE INDEX IF NOT EXISTS idx_dispatch_offers_status ON dispatch_offers(status);

-- City configurations
CREATE TABLE IF NOT EXISTS city_configs (
    city_id             VARCHAR(50) PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    timezone            VARCHAR(50) NOT NULL,
    center_latitude     DOUBLE PRECISION NOT NULL,
    center_longitude    DOUBLE PRECISION NOT NULL,
    boundary            GEOGRAPHY(POLYGON, 4326),
    default_radius_m    INTEGER DEFAULT 5000,
    max_radius_m        INTEGER DEFAULT 20000,
    surge_enabled       BOOLEAN DEFAULT TRUE,
    min_fare            DECIMAL(10,2) DEFAULT 5.00,
    per_km_rate         DECIMAL(10,4) DEFAULT 1.50,
    per_minute_rate     DECIMAL(10,4) DEFAULT 0.25,
    active              BOOLEAN DEFAULT TRUE,
    created_at          TIMESTAMPTZ DEFAULT NOW(),
    updated_at          TIMESTAMPTZ DEFAULT NOW()
);

-- Surge pricing zones (for dynamic pricing)
CREATE TABLE IF NOT EXISTS surge_zones (
    zone_id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    city_id         VARCHAR(50) NOT NULL REFERENCES city_configs(city_id),
    h3_index        BIGINT NOT NULL,
    surge_multiplier DECIMAL(4,2) DEFAULT 1.00,
    demand_level    INTEGER DEFAULT 0,
    supply_level    INTEGER DEFAULT 0,
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (city_id, h3_index)
);

CREATE INDEX IF NOT EXISTS idx_surge_zones_city ON surge_zones(city_id);
CREATE INDEX IF NOT EXISTS idx_surge_zones_h3 ON surge_zones(h3_index);

-- Function to auto-update location geography from lat/lng
CREATE OR REPLACE FUNCTION update_driver_location_geography()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for driver_locations
DROP TRIGGER IF EXISTS trg_update_driver_location_geo ON driver_locations;
CREATE TRIGGER trg_update_driver_location_geo
    BEFORE INSERT OR UPDATE ON driver_locations
    FOR EACH ROW
    EXECUTE FUNCTION update_driver_location_geography();

-- Trigger for driver_location_history
DROP TRIGGER IF EXISTS trg_update_driver_location_history_geo ON driver_location_history;
CREATE TRIGGER trg_update_driver_location_history_geo
    BEFORE INSERT OR UPDATE ON driver_location_history
    FOR EACH ROW
    EXECUTE FUNCTION update_driver_location_geography();

-- Function to find nearby drivers using PostGIS
CREATE OR REPLACE FUNCTION find_nearby_drivers(
    p_lat DOUBLE PRECISION,
    p_lng DOUBLE PRECISION,
    p_radius_m INTEGER DEFAULT 5000,
    p_vehicle_type VARCHAR DEFAULT NULL,
    p_limit INTEGER DEFAULT 20
)
RETURNS TABLE (
    driver_id UUID,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_m DOUBLE PRECISION,
    heading SMALLINT,
    speed REAL,
    vehicle_type VARCHAR,
    rating DECIMAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        dl.driver_id,
        dl.latitude,
        dl.longitude,
        ST_Distance(
            dl.location,
            ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography
        ) AS distance_m,
        dl.heading,
        dl.speed,
        d.vehicle_type,
        d.rating
    FROM driver_locations dl
    JOIN drivers d ON dl.driver_id = d.driver_id
    WHERE dl.status = 'AVAILABLE'
      AND ST_DWithin(
          dl.location,
          ST_SetSRID(ST_MakePoint(p_lng, p_lat), 4326)::geography,
          p_radius_m
      )
      AND (p_vehicle_type IS NULL OR d.vehicle_type = p_vehicle_type)
    ORDER BY distance_m
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

-- Sample data for testing
INSERT INTO city_configs (city_id, name, timezone, center_latitude, center_longitude, default_radius_m)
VALUES 
    ('san_francisco', 'San Francisco', 'America/Los_Angeles', 37.7749, -122.4194, 5000),
    ('new_york', 'New York', 'America/New_York', 40.7128, -74.0060, 3000),
    ('los_angeles', 'Los Angeles', 'America/Los_Angeles', 34.0522, -118.2437, 8000)
ON CONFLICT (city_id) DO NOTHING;
