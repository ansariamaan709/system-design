-- ============================================================
-- YouTube MySQL Schema
-- How YouTube Scaled to 2.49 Billion Users Using MySQL
-- ============================================================

-- Create database
CREATE DATABASE IF NOT EXISTS youtube CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE youtube;

-- ============================================================
-- KEYSPACE: youtube_videos (would be sharded by video_id in Vitess)
-- ============================================================

-- Videos table
CREATE TABLE IF NOT EXISTS videos (
    video_id BIGINT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    duration_seconds INT NOT NULL DEFAULT 0,
    
    -- Video metadata
    upload_status ENUM('PROCESSING', 'PUBLISHED', 'PRIVATE', 'UNLISTED', 'DELETED') DEFAULT 'PROCESSING',
    visibility ENUM('PUBLIC', 'PRIVATE', 'UNLISTED') DEFAULT 'PUBLIC',
    category_id INT,
    default_language VARCHAR(10) DEFAULT 'en',
    
    -- Thumbnails & assets
    thumbnail_url VARCHAR(512),
    preview_url VARCHAR(512),
    
    -- Monetization
    monetization_enabled BOOLEAN DEFAULT FALSE,
    ad_suitability ENUM('FULL', 'LIMITED', 'NONE') DEFAULT 'FULL',
    
    -- Content flags
    is_live_stream BOOLEAN DEFAULT FALSE,
    is_premiere BOOLEAN DEFAULT FALSE,
    is_short BOOLEAN DEFAULT FALSE,
    age_restricted BOOLEAN DEFAULT FALSE,
    
    -- Timestamps
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_channel_id (channel_id),
    INDEX idx_published_at (published_at),
    INDEX idx_category (category_id, published_at),
    INDEX idx_upload_status (upload_status),
    INDEX idx_visibility (visibility, published_at)
) ENGINE=InnoDB;

-- Video statistics (denormalized for performance)
CREATE TABLE IF NOT EXISTS video_stats (
    video_id BIGINT PRIMARY KEY,
    view_count BIGINT UNSIGNED DEFAULT 0,
    like_count BIGINT UNSIGNED DEFAULT 0,
    dislike_count BIGINT UNSIGNED DEFAULT 0,
    comment_count BIGINT UNSIGNED DEFAULT 0,
    share_count BIGINT UNSIGNED DEFAULT 0,
    
    -- Engagement metrics
    avg_view_duration_seconds INT DEFAULT 0,
    avg_percentage_viewed DECIMAL(5,2) DEFAULT 0,
    
    -- Revenue (for creators)
    estimated_revenue_micros BIGINT DEFAULT 0,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_view_count (view_count DESC),
    INDEX idx_like_count (like_count DESC)
) ENGINE=InnoDB;

-- Video transcoding outputs
CREATE TABLE IF NOT EXISTS video_formats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    format_code VARCHAR(20) NOT NULL,
    codec VARCHAR(50) NOT NULL,
    container VARCHAR(20) NOT NULL,
    bitrate_kbps INT NOT NULL,
    width INT,
    height INT,
    file_size_bytes BIGINT NOT NULL,
    storage_url VARCHAR(512) NOT NULL,
    cdn_url VARCHAR(512),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_video_format (video_id, format_code, codec),
    INDEX idx_video_id (video_id),
    
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Video tags
CREATE TABLE IF NOT EXISTS video_tags (
    video_id BIGINT NOT NULL,
    tag VARCHAR(100) NOT NULL,
    
    PRIMARY KEY (video_id, tag),
    INDEX idx_tag (tag),
    
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- KEYSPACE: youtube_users (would be sharded by user_id in Vitess)
-- ============================================================

-- Users table
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT PRIMARY KEY,
    google_account_id VARCHAR(255) UNIQUE,
    email VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    profile_image_url VARCHAR(512),
    
    -- Account status
    account_status ENUM('ACTIVE', 'SUSPENDED', 'TERMINATED') DEFAULT 'ACTIVE',
    verification_status ENUM('NONE', 'VERIFIED', 'OFFICIAL') DEFAULT 'NONE',
    
    -- Settings
    country_code VARCHAR(2),
    preferred_language VARCHAR(10) DEFAULT 'en',
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_email (email),
    INDEX idx_google_account (google_account_id),
    INDEX idx_last_active (last_active_at)
) ENGINE=InnoDB;

-- Channels table
CREATE TABLE IF NOT EXISTS channels (
    channel_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    handle VARCHAR(50) UNIQUE,
    custom_url VARCHAR(100),
    
    title VARCHAR(100) NOT NULL,
    description TEXT,
    banner_url VARCHAR(512),
    avatar_url VARCHAR(512),
    
    -- Channel stats (denormalized)
    subscriber_count BIGINT UNSIGNED DEFAULT 0,
    video_count INT UNSIGNED DEFAULT 0,
    total_views BIGINT UNSIGNED DEFAULT 0,
    
    -- Monetization
    is_monetized BOOLEAN DEFAULT FALSE,
    partner_since TIMESTAMP NULL,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_handle (handle),
    INDEX idx_subscriber_count (subscriber_count DESC),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Subscriptions (sharded by user_id)
CREATE TABLE IF NOT EXISTS subscriptions (
    user_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    notification_level ENUM('ALL', 'PERSONALIZED', 'NONE') DEFAULT 'PERSONALIZED',
    subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (user_id, channel_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_subscribed_at (user_id, subscribed_at DESC),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Watch history
CREATE TABLE IF NOT EXISTS watch_history (
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    watched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    watch_duration_seconds INT DEFAULT 0,
    percentage_watched DECIMAL(5,2) DEFAULT 0,
    
    PRIMARY KEY (user_id, video_id),
    INDEX idx_watched_at (user_id, watched_at DESC),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Playlists
CREATE TABLE IF NOT EXISTS playlists (
    playlist_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    visibility ENUM('PUBLIC', 'PRIVATE', 'UNLISTED') DEFAULT 'PRIVATE',
    video_count INT UNSIGNED DEFAULT 0,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_id (user_id),
    INDEX idx_visibility (visibility, updated_at DESC),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Playlist items
CREATE TABLE IF NOT EXISTS playlist_items (
    playlist_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    position INT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (playlist_id, video_id),
    INDEX idx_position (playlist_id, position),
    
    FOREIGN KEY (playlist_id) REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- KEYSPACE: youtube_social (would be sharded by video_id/entity_id)
-- ============================================================

-- Comments
CREATE TABLE IF NOT EXISTS comments (
    comment_id BIGINT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,
    
    content TEXT NOT NULL,
    like_count INT UNSIGNED DEFAULT 0,
    reply_count INT UNSIGNED DEFAULT 0,
    
    -- Moderation
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED', 'SPAM') DEFAULT 'VISIBLE',
    is_pinned BOOLEAN DEFAULT FALSE,
    is_hearted BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_video_id (video_id, created_at DESC),
    INDEX idx_video_top (video_id, like_count DESC),
    INDEX idx_parent (parent_comment_id),
    INDEX idx_user_id (user_id),
    
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Video likes/dislikes
CREATE TABLE IF NOT EXISTS video_likes (
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    like_type ENUM('LIKE', 'DISLIKE') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (video_id, user_id),
    INDEX idx_user_id (user_id),
    
    FOREIGN KEY (video_id) REFERENCES videos(video_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Comment likes
CREATE TABLE IF NOT EXISTS comment_likes (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (comment_id, user_id),
    INDEX idx_user_id (user_id),
    
    FOREIGN KEY (comment_id) REFERENCES comments(comment_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ============================================================
-- Lookup/Index tables (for Vitess secondary vindexes)
-- ============================================================

-- Channel to video index (for routing channel queries)
CREATE TABLE IF NOT EXISTS channel_video_idx (
    channel_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    
    PRIMARY KEY (channel_id, video_id),
    INDEX idx_video_id (video_id)
) ENGINE=InnoDB;

-- ============================================================
-- Categories (reference data)
-- ============================================================

CREATE TABLE IF NOT EXISTS categories (
    category_id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_category_id INT NULL,
    
    INDEX idx_parent (parent_category_id)
) ENGINE=InnoDB;

-- Insert default categories
INSERT INTO categories (category_id, name) VALUES
(1, 'Film & Animation'),
(2, 'Autos & Vehicles'),
(10, 'Music'),
(15, 'Pets & Animals'),
(17, 'Sports'),
(18, 'Short Movies'),
(19, 'Travel & Events'),
(20, 'Gaming'),
(21, 'Videoblogging'),
(22, 'People & Blogs'),
(23, 'Comedy'),
(24, 'Entertainment'),
(25, 'News & Politics'),
(26, 'Howto & Style'),
(27, 'Education'),
(28, 'Science & Technology'),
(29, 'Nonprofits & Activism')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- ============================================================
-- View Count Aggregation Table (for periodic flushes)
-- ============================================================

CREATE TABLE IF NOT EXISTS view_count_buffer (
    video_id BIGINT NOT NULL,
    view_delta BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    PRIMARY KEY (video_id)
) ENGINE=InnoDB;

-- ============================================================
-- Stored Procedures
-- ============================================================

DELIMITER //

-- Flush view counts from buffer to video_stats
CREATE PROCEDURE IF NOT EXISTS flush_view_counts()
BEGIN
    -- Update video_stats from buffer
    UPDATE video_stats vs
    JOIN view_count_buffer vcb ON vs.video_id = vcb.video_id
    SET vs.view_count = vs.view_count + vcb.view_delta,
        vs.updated_at = NOW();
    
    -- Clear buffer
    TRUNCATE TABLE view_count_buffer;
END //

-- Increment view count in buffer (for batching)
CREATE PROCEDURE IF NOT EXISTS increment_view_count(IN p_video_id BIGINT, IN p_delta BIGINT)
BEGIN
    INSERT INTO view_count_buffer (video_id, view_delta)
    VALUES (p_video_id, p_delta)
    ON DUPLICATE KEY UPDATE view_delta = view_delta + p_delta;
END //

-- Update channel stats (denormalized)
CREATE PROCEDURE IF NOT EXISTS update_channel_stats(IN p_channel_id BIGINT)
BEGIN
    UPDATE channels c
    SET 
        video_count = (SELECT COUNT(*) FROM videos WHERE channel_id = p_channel_id AND upload_status = 'PUBLISHED'),
        total_views = (SELECT COALESCE(SUM(vs.view_count), 0) 
                       FROM videos v 
                       JOIN video_stats vs ON v.video_id = vs.video_id 
                       WHERE v.channel_id = p_channel_id)
    WHERE c.channel_id = p_channel_id;
END //

DELIMITER ;

-- ============================================================
-- Triggers
-- ============================================================

DELIMITER //

-- Auto-create video_stats entry when video is created
CREATE TRIGGER IF NOT EXISTS after_video_insert
AFTER INSERT ON videos
FOR EACH ROW
BEGIN
    INSERT INTO video_stats (video_id) VALUES (NEW.video_id);
    INSERT INTO channel_video_idx (channel_id, video_id) VALUES (NEW.channel_id, NEW.video_id);
END //

-- Update comment count on video_stats
CREATE TRIGGER IF NOT EXISTS after_comment_insert
AFTER INSERT ON comments
FOR EACH ROW
BEGIN
    UPDATE video_stats SET comment_count = comment_count + 1 WHERE video_id = NEW.video_id;
    IF NEW.parent_comment_id IS NOT NULL THEN
        UPDATE comments SET reply_count = reply_count + 1 WHERE comment_id = NEW.parent_comment_id;
    END IF;
END //

-- Update like count on video_stats
CREATE TRIGGER IF NOT EXISTS after_video_like_insert
AFTER INSERT ON video_likes
FOR EACH ROW
BEGIN
    IF NEW.like_type = 'LIKE' THEN
        UPDATE video_stats SET like_count = like_count + 1 WHERE video_id = NEW.video_id;
    ELSE
        UPDATE video_stats SET dislike_count = dislike_count + 1 WHERE video_id = NEW.video_id;
    END IF;
END //

-- Update subscriber count on channel
CREATE TRIGGER IF NOT EXISTS after_subscription_insert
AFTER INSERT ON subscriptions
FOR EACH ROW
BEGIN
    UPDATE channels SET subscriber_count = subscriber_count + 1 WHERE channel_id = NEW.channel_id;
END //

CREATE TRIGGER IF NOT EXISTS after_subscription_delete
AFTER DELETE ON subscriptions
FOR EACH ROW
BEGIN
    UPDATE channels SET subscriber_count = subscriber_count - 1 WHERE channel_id = OLD.channel_id;
END //

DELIMITER ;
