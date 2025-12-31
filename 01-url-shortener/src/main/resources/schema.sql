-- PostgreSQL Schema for URL Shortener
-- Run this script to initialize the database

-- Create database (if not exists, run separately)
-- CREATE DATABASE urlshortener;

-- URLs table
CREATE TABLE IF NOT EXISTS urls (
    id BIGINT PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL UNIQUE,
    original_url TEXT NOT NULL,
    user_id VARCHAR(255),
    click_count BIGINT DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    is_custom_alias BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Click events table for analytics
CREATE TABLE IF NOT EXISTS click_events (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referer TEXT,
    country VARCHAR(100),
    city VARCHAR(100),
    device_type VARCHAR(50),
    browser VARCHAR(100),
    os VARCHAR(100),
    clicked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_urls_short_code ON urls(short_code);
CREATE INDEX IF NOT EXISTS idx_urls_user_id ON urls(user_id);
CREATE INDEX IF NOT EXISTS idx_urls_created_at ON urls(created_at);
CREATE INDEX IF NOT EXISTS idx_urls_expires_at ON urls(expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_urls_active ON urls(is_active) WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_click_events_short_code ON click_events(short_code);
CREATE INDEX IF NOT EXISTS idx_click_events_clicked_at ON click_events(clicked_at);
CREATE INDEX IF NOT EXISTS idx_click_events_country ON click_events(country);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Trigger to auto-update updated_at
DROP TRIGGER IF EXISTS update_urls_updated_at ON urls;
CREATE TRIGGER update_urls_updated_at
    BEFORE UPDATE ON urls
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Sample data for testing (optional)
-- INSERT INTO urls (id, short_code, original_url, user_id, click_count, is_active)
-- VALUES 
--     (1, 'abc123X', 'https://www.google.com', 'user1', 100, true),
--     (2, 'def456Y', 'https://www.github.com', 'user1', 50, true),
--     (3, 'ghi789Z', 'https://www.stackoverflow.com', 'user2', 200, true);
