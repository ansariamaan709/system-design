# How YouTube Scaled to 2.49 Billion Users Using MySQL

## System Design: Video Platform Database Architecture at Planetary Scale

---

## 1. Problem Statement & Scale

### The Challenge

YouTube serves **2.49 billion monthly active users**, with **500+ hours of video uploaded every minute** and **1 billion hours watched daily**. How do you build a database system that handles this scale while maintaining consistency, low latency, and high availability?

### Scale Requirements

| Metric                    | Value           |
| ------------------------- | --------------- |
| Monthly Active Users      | 2.49 billion    |
| Daily Active Users        | 122 million     |
| Videos Watched Daily      | 1 billion hours |
| Video Uploads per Minute  | 500+ hours      |
| Total Videos              | 800+ million    |
| Comments per Day          | 500+ million    |
| Likes per Day             | 5+ billion      |
| Search Queries per Second | 100,000+        |
| API Requests per Second   | 10+ million     |

### Why MySQL?

YouTube chose MySQL over NoSQL because:

1. **ACID Transactions** - Critical for payments, subscriptions, monetization
2. **Complex Queries** - JOIN operations for recommendations, analytics
3. **Strong Consistency** - Required for view counts, subscriber counts
4. **Proven Reliability** - Battle-tested at scale
5. **Developer Familiarity** - Easier hiring, tooling, debugging

### The Solution: Vitess

YouTube developed **Vitess** - a database clustering system that:

- Horizontally shards MySQL across thousands of nodes
- Provides transparent query routing
- Handles connection pooling at massive scale
- Enables online schema migrations
- Supports cross-shard transactions

---

## 2. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT LAYER                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  Web App    │   Mobile Apps   │   Creator Studio   │   YouTube TV          │
└──────┬──────┴────────┬────────┴─────────┬──────────┴──────────┬────────────┘
       │               │                  │                     │
       └───────────────┴──────────────────┴─────────────────────┘
                                  │
                    ┌─────────────▼─────────────┐
                    │      API Gateway          │
                    │   (Rate Limiting, Auth)   │
                    └─────────────┬─────────────┘
                                  │
       ┌──────────────────────────┼──────────────────────────┐
       │                          │                          │
┌──────▼──────┐          ┌───────▼───────┐          ┌───────▼───────┐
│   Video     │          │    User       │          │   Social      │
│   Service   │          │    Service    │          │   Service     │
└──────┬──────┘          └───────┬───────┘          └───────┬───────┘
       │                         │                          │
       └─────────────────────────┼──────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │         VTGate          │
                    │   (Query Router/Proxy)  │
                    └────────────┬────────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        │                        │                        │
┌───────▼───────┐        ┌──────▼──────┐         ┌───────▼───────┐
│   VTTablet    │        │  VTTablet   │         │   VTTablet    │
│   (Shard 0)   │        │  (Shard 1)  │         │   (Shard N)   │
├───────────────┤        ├─────────────┤         ├───────────────┤
│ ┌───────────┐ │        │┌───────────┐│         │ ┌───────────┐ │
│ │  Primary  │ │        ││  Primary  ││         │ │  Primary  │ │
│ │   MySQL   │ │        ││   MySQL   ││         │ │   MySQL   │ │
│ └─────┬─────┘ │        │└─────┬─────┘│         │ └─────┬─────┘ │
│       │       │        │      │      │         │       │       │
│ ┌─────▼─────┐ │        │┌─────▼─────┐│         │ ┌─────▼─────┐ │
│ │ Replica 1 │ │        ││ Replica 1 ││         │ │ Replica 1 │ │
│ └───────────┘ │        │└───────────┘│         │ └───────────┘ │
│ ┌───────────┐ │        │┌───────────┐│         │ ┌───────────┐ │
│ │ Replica 2 │ │        ││ Replica 2 ││         │ │ Replica 2 │ │
│ └───────────┘ │        │└───────────┘│         │ └───────────┘ │
└───────────────┘        └─────────────┘         └───────────────┘
```

### Vitess Components

| Component    | Purpose                                             |
| ------------ | --------------------------------------------------- |
| **VTGate**   | Query router, connection pooler, query rewriter     |
| **VTTablet** | MySQL sidecar managing replication, backups, schema |
| **Topology** | Metadata store (etcd/ZooKeeper/Consul)              |
| **VTCtld**   | Cluster management daemon                           |
| **VTAdmin**  | Web UI for cluster administration                   |

---

## 3. Data Models & Schema Design

### Sharding Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                     KEYSPACE: youtube_videos                     │
├─────────────────────────────────────────────────────────────────┤
│  Sharding Key: video_id (hash-based)                            │
│  Shards: 256 (can split/merge dynamically)                      │
│                                                                  │
│  Shard 00: video_id hash 0x00 - 0x0F                           │
│  Shard 01: video_id hash 0x10 - 0x1F                           │
│  ...                                                             │
│  Shard FF: video_id hash 0xF0 - 0xFF                           │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     KEYSPACE: youtube_users                      │
├─────────────────────────────────────────────────────────────────┤
│  Sharding Key: user_id (hash-based)                             │
│  Shards: 128                                                     │
│                                                                  │
│  Contains: users, subscriptions, playlists, watch_history       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    KEYSPACE: youtube_social                      │
├─────────────────────────────────────────────────────────────────┤
│  Sharding Key: entity_id (video_id for comments/likes)          │
│  Shards: 512 (high write volume)                                │
│                                                                  │
│  Contains: comments, likes, community_posts                      │
└─────────────────────────────────────────────────────────────────┘
```

### Core Tables

```sql
-- ============================================================
-- KEYSPACE: youtube_videos (sharded by video_id)
-- ============================================================

CREATE TABLE videos (
    video_id BIGINT PRIMARY KEY,
    channel_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    duration_seconds INT NOT NULL DEFAULT 0,

    -- Video metadata
    upload_status ENUM('PROCESSING', 'PUBLISHED', 'PRIVATE', 'UNLISTED', 'DELETED') DEFAULT 'PROCESSING',
    visibility ENUM('PUBLIC', 'PRIVATE', 'UNLISTED') DEFAULT 'PUBLIC',
    category_id INT,
    default_language VARCHAR(10),

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
    INDEX idx_category (category_id, published_at)
) ENGINE=InnoDB;

-- Denormalized counters (updated asynchronously)
CREATE TABLE video_stats (
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

    INDEX idx_view_count (view_count DESC)
) ENGINE=InnoDB;

-- Video transcoding outputs
CREATE TABLE video_formats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    format_code VARCHAR(20) NOT NULL,  -- '360p', '720p', '1080p', '4k', 'audio_only'
    codec VARCHAR(50) NOT NULL,         -- 'h264', 'vp9', 'av1'
    container VARCHAR(20) NOT NULL,     -- 'mp4', 'webm'
    bitrate_kbps INT NOT NULL,
    width INT,
    height INT,
    file_size_bytes BIGINT NOT NULL,
    storage_url VARCHAR(512) NOT NULL,
    cdn_url VARCHAR(512),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_video_format (video_id, format_code, codec),
    INDEX idx_video_id (video_id)
) ENGINE=InnoDB;

-- ============================================================
-- KEYSPACE: youtube_users (sharded by user_id)
-- ============================================================

CREATE TABLE users (
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
    INDEX idx_last_active (last_active_at)
) ENGINE=InnoDB;

CREATE TABLE channels (
    channel_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    handle VARCHAR(50) UNIQUE,          -- @handle
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
    INDEX idx_subscriber_count (subscriber_count DESC)
) ENGINE=InnoDB;

-- User subscriptions (sharded by user_id for "my subscriptions" queries)
CREATE TABLE subscriptions (
    user_id BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    notification_level ENUM('ALL', 'PERSONALIZED', 'NONE') DEFAULT 'PERSONALIZED',
    subscribed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, channel_id),
    INDEX idx_channel_id (channel_id),
    INDEX idx_subscribed_at (user_id, subscribed_at DESC)
) ENGINE=InnoDB;

-- Watch history (sharded by user_id)
CREATE TABLE watch_history (
    user_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    watched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    watch_duration_seconds INT DEFAULT 0,
    percentage_watched DECIMAL(5,2) DEFAULT 0,

    PRIMARY KEY (user_id, video_id),
    INDEX idx_watched_at (user_id, watched_at DESC)
) ENGINE=InnoDB;

-- User playlists
CREATE TABLE playlists (
    playlist_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    visibility ENUM('PUBLIC', 'PRIVATE', 'UNLISTED') DEFAULT 'PRIVATE',
    video_count INT UNSIGNED DEFAULT 0,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_id (user_id),
    INDEX idx_visibility (visibility, updated_at DESC)
) ENGINE=InnoDB;

CREATE TABLE playlist_items (
    playlist_id BIGINT NOT NULL,
    video_id BIGINT NOT NULL,
    position INT NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (playlist_id, video_id),
    INDEX idx_position (playlist_id, position)
) ENGINE=InnoDB;

-- ============================================================
-- KEYSPACE: youtube_social (sharded by video_id/entity_id)
-- ============================================================

-- Comments (sharded by video_id for efficient video page loads)
CREATE TABLE comments (
    comment_id BIGINT PRIMARY KEY,
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,      -- NULL for top-level comments

    content TEXT NOT NULL,
    like_count INT UNSIGNED DEFAULT 0,
    reply_count INT UNSIGNED DEFAULT 0,

    -- Moderation
    status ENUM('VISIBLE', 'HIDDEN', 'DELETED', 'SPAM') DEFAULT 'VISIBLE',
    is_pinned BOOLEAN DEFAULT FALSE,
    is_hearted BOOLEAN DEFAULT FALSE,   -- Creator hearted

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_video_id (video_id, created_at DESC),
    INDEX idx_video_top (video_id, like_count DESC),
    INDEX idx_parent (parent_comment_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- Likes/dislikes (sharded by video_id)
CREATE TABLE video_likes (
    video_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    like_type ENUM('LIKE', 'DISLIKE') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (video_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- Comment likes
CREATE TABLE comment_likes (
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (comment_id, user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;
```

### Vindex (Vitess Sharding Functions)

```yaml
# VSchema definition for youtube_videos keyspace
{
  "sharded": true,
  "vindexes":
    {
      "video_hash": { "type": "xxhash64" },
      "channel_lookup":
        {
          "type": "lookup_hash_unique",
          "params":
            {
              "table": "channel_video_idx",
              "from": "channel_id",
              "to": "video_id",
            },
          "owner": "videos",
        },
    },
  "tables":
    {
      "videos":
        {
          "column_vindexes":
            [
              { "column": "video_id", "name": "video_hash" },
              { "column": "channel_id", "name": "channel_lookup" },
            ],
        },
      "video_stats":
        { "column_vindexes": [{ "column": "video_id", "name": "video_hash" }] },
    },
}
```

---

## 4. Core Algorithms

### 4.1 Distributed ID Generation (Snowflake-style)

```
┌─────────────────────────────────────────────────────────────────┐
│                    64-bit YouTube ID                             │
├──────────┬───────────────┬──────────────┬───────────────────────┤
│  1 bit   │    41 bits    │   10 bits    │       12 bits         │
│  unused  │  timestamp    │  machine ID  │    sequence num       │
├──────────┼───────────────┼──────────────┼───────────────────────┤
│    0     │ ms since epoch│  datacenter  │   counter per ms      │
│          │ (69 years)    │  + worker    │   (4096/ms/worker)    │
└──────────┴───────────────┴──────────────┴───────────────────────┘

Capacity: 4,096 IDs/ms/worker × 1,024 workers = 4.1M IDs/second
```

### 4.2 View Count Aggregation

```
Problem: 1 billion hours watched = ~15 billion view events daily
Solution: Multi-tier aggregation with eventual consistency

┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Client     │────▶│   Kafka      │────▶│   Flink      │
│  View Event  │     │   Topic      │     │  Aggregator  │
└──────────────┘     └──────────────┘     └──────┬───────┘
                                                  │
                     ┌────────────────────────────┼────────────────────┐
                     │                            │                    │
              ┌──────▼──────┐            ┌───────▼───────┐    ┌───────▼───────┐
              │   Redis     │            │    MySQL      │    │   BigQuery    │
              │  (Real-time)│            │   (Periodic)  │    │  (Analytics)  │
              │  ~1s delay  │            │  ~5min delay  │    │  ~1hr delay   │
              └─────────────┘            └───────────────┘    └───────────────┘

View Count Display Logic:
- < 1000 views: Show exact count
- 1K - 1M: Round to nearest hundred (1.2K)
- > 1M: Round to nearest thousand (1.2M)
```

### 4.3 Scatter-Gather Query Pattern

```
Client Request: GET /channels/{channel_id}/videos?sort=views

                        ┌─────────────┐
                        │   VTGate    │
                        └──────┬──────┘
                               │
         ┌─────────────────────┼─────────────────────┐
         │                     │                     │
    ┌────▼────┐          ┌────▼────┐          ┌────▼────┐
    │ Shard 0 │          │ Shard 1 │          │ Shard N │
    │ LIMIT 10│          │ LIMIT 10│          │ LIMIT 10│
    └────┬────┘          └────┬────┘          └────┬────┘
         │                     │                     │
         └─────────────────────┼─────────────────────┘
                               │
                        ┌──────▼──────┐
                        │   VTGate    │
                        │  Merge &    │
                        │  Sort Top 10│
                        └─────────────┘

Optimization: Secondary index table (channel_video_idx)
              routes query to specific shards
```

### 4.4 Online Schema Migration

```
Traditional ALTER TABLE: Locks table, causes downtime
YouTube's Approach: Ghost tables with incremental copy

Step 1: Create ghost table with new schema
┌─────────────────┐        ┌─────────────────────────┐
│  videos (old)   │        │  _videos_new (ghost)    │
│  - id           │        │  - id                   │
│  - title        │  ───▶  │  - title                │
│  - views        │        │  - views                │
│                 │        │  - new_column (added)   │
└─────────────────┘        └─────────────────────────┘

Step 2: Binary log replication + incremental copy
- Copy existing rows in batches
- Apply binlog changes to ghost table
- Minimal locking during copy

Step 3: Atomic table swap
RENAME TABLE videos TO _videos_old, _videos_new TO videos;

Step 4: Drop old table (async)
```

---

## 5. Query Routing & Optimization

### Query Classification

```java
public enum QueryType {
    // Routes to single shard (best performance)
    POINT_LOOKUP,       // WHERE video_id = ?

    // Routes to multiple specific shards
    IN_QUERY,           // WHERE video_id IN (?, ?, ?)

    // Scatter to all shards (expensive)
    SCATTER_GATHER,     // WHERE channel_id = ? (without lookup vindex)

    // Full table scan (avoid in production)
    FULL_SCAN           // SELECT * FROM videos
}
```

### Query Rewriting

```sql
-- Original query (user submits)
SELECT * FROM videos
WHERE channel_id = 12345
ORDER BY published_at DESC
LIMIT 20;

-- Vitess rewrites to (if lookup vindex exists)
-- Step 1: Lookup video_ids for channel
SELECT video_id FROM channel_video_idx WHERE channel_id = 12345;

-- Step 2: Route to specific shards containing those video_ids
SELECT * FROM videos WHERE video_id IN (v1, v2, v3, ...)
ORDER BY published_at DESC LIMIT 20;
```

### Connection Pooling

```
Without Vitess:
┌────────────┐
│  App (N    │──── N × M connections ────▶ M MySQL instances
│  instances)│     = 10,000 × 100 = 1M connections ❌
└────────────┘

With Vitess VTGate:
┌────────────┐       ┌─────────┐
│  App (N    │──N───▶│ VTGate  │──100──▶ M MySQL instances
│  instances)│       │ (pool)  │         = 100 × 100 = 10K connections ✓
└────────────┘       └─────────┘
```

---

## 6. Consistency & Replication

### Replication Topology

```
                    ┌─────────────────────────────────┐
                    │           PRIMARY               │
                    │     (Writes + Strong Reads)     │
                    └───────────────┬─────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
            ┌───────▼───────┐ ┌────▼────┐ ┌───────▼───────┐
            │   REPLICA 1   │ │REPLICA 2│ │   REPLICA 3   │
            │  (Same DC)    │ │(Same DC)│ │  (Cross-DC)   │
            │  ~0ms lag     │ │~0ms lag │ │  ~100ms lag   │
            └───────────────┘ └─────────┘ └───────────────┘
                   │              │               │
                   └──────────────┴───────────────┘
                                  │
                          ┌───────▼───────┐
                          │  Read Traffic │
                          │   (90%+)      │
                          └───────────────┘
```

### Read-Your-Writes Consistency

```java
// Problem: User uploads video, immediately views their channel
// Risk: Read from replica misses recent write

// Solution: Session-based routing
public class ConsistencyManager {

    // Track transaction timestamps per session
    private final Map<String, Long> sessionTimestamps = new ConcurrentHashMap<>();

    public void recordWrite(String sessionId, long gtid) {
        sessionTimestamps.put(sessionId, gtid);
    }

    public QueryTarget selectTarget(String sessionId, Query query) {
        if (query.isWrite()) {
            return QueryTarget.PRIMARY;
        }

        Long lastWrite = sessionTimestamps.get(sessionId);
        if (lastWrite != null && !replicaCaughtUp(lastWrite)) {
            // Replica hasn't caught up, route to primary
            return QueryTarget.PRIMARY;
        }

        return QueryTarget.REPLICA;
    }
}
```

### Conflict Resolution for Cross-DC

```
┌────────────────────────────────────────────────────────────────┐
│                    CONFLICT SCENARIOS                           │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Scenario 1: Same video edited in two DCs                      │
│  ┌─────────┐          ┌─────────┐                              │
│  │  DC-US  │          │  DC-EU  │                              │
│  │ title=A │          │ title=B │                              │
│  └────┬────┘          └────┬────┘                              │
│       │                    │                                    │
│       └────────┬───────────┘                                   │
│                ▼                                                │
│         Last-Write-Wins (LWW)                                  │
│         Based on timestamp                                      │
│                                                                 │
│  Scenario 2: Counter increment in two DCs                      │
│  ┌─────────┐          ┌─────────┐                              │
│  │  DC-US  │          │  DC-EU  │                              │
│  │ views++ │          │ views++ │                              │
│  └────┬────┘          └────┬────┘                              │
│       │                    │                                    │
│       └────────┬───────────┘                                   │
│                ▼                                                │
│         CRDT Counter                                           │
│         (Merge: sum of increments)                             │
└────────────────────────────────────────────────────────────────┘
```

---

## 7. Caching Strategy

### Multi-Level Cache Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        L1: Application Cache                     │
│                        (Caffeine, 10K items)                    │
│                        Hit Rate: 30-40%                          │
│                        Latency: <1ms                             │
└───────────────────────────────┬─────────────────────────────────┘
                                │ MISS
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        L2: Redis Cluster                         │
│                        (Distributed, 1TB)                        │
│                        Hit Rate: 60-70%                          │
│                        Latency: 1-5ms                            │
└───────────────────────────────┬─────────────────────────────────┘
                                │ MISS
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                        L3: MySQL (via Vitess)                    │
│                        Latency: 10-50ms                          │
└─────────────────────────────────────────────────────────────────┘
```

### Cache Key Design

```java
public class CacheKeyGenerator {

    // Video metadata (TTL: 5 minutes)
    public static String videoKey(long videoId) {
        return String.format("v:meta:%d", videoId);
    }

    // Video stats (TTL: 30 seconds - frequently updated)
    public static String videoStatsKey(long videoId) {
        return String.format("v:stats:%d", videoId);
    }

    // Channel info (TTL: 10 minutes)
    public static String channelKey(long channelId) {
        return String.format("c:info:%d", channelId);
    }

    // User subscriptions (TTL: 5 minutes)
    public static String userSubscriptionsKey(long userId) {
        return String.format("u:subs:%d", userId);
    }

    // Hot videos list (TTL: 1 minute)
    public static String trendingKey(String region) {
        return String.format("trending:%s", region);
    }
}
```

### Cache Invalidation

```java
@Component
public class CacheInvalidationListener {

    @KafkaListener(topics = "video-updates")
    public void onVideoUpdate(VideoUpdateEvent event) {
        // Invalidate all related caches
        cacheManager.evict(CacheKeyGenerator.videoKey(event.getVideoId()));
        cacheManager.evict(CacheKeyGenerator.videoStatsKey(event.getVideoId()));

        // Invalidate channel's video list
        cacheManager.evict(
            CacheKeyGenerator.channelVideosKey(event.getChannelId())
        );
    }

    @KafkaListener(topics = "view-events")
    public void onViewEvent(ViewEvent event) {
        // Don't invalidate on every view - use write-behind
        // Stats cache has short TTL anyway
    }
}
```

---

## 8. Write Path Optimization

### Batch Insert Pipeline

```
┌────────────┐     ┌─────────────┐     ┌──────────────┐     ┌─────────┐
│ Comment    │────▶│   Kafka     │────▶│   Consumer   │────▶│  MySQL  │
│ Service    │     │   Buffer    │     │   (Batch)    │     │  Batch  │
│ 10K/sec    │     │             │     │   1000 rows  │     │  INSERT │
└────────────┘     └─────────────┘     └──────────────┘     └─────────┘

Batching reduces:
- Network round trips: 10K → 10
- Transaction overhead: 10K → 10
- Index updates: Bulk instead of individual
```

### Write-Behind Caching

```java
@Service
public class ViewCountService {

    private final RedisTemplate<String, Long> redis;
    private final ScheduledExecutorService scheduler;

    // Increment in Redis (fast)
    public void incrementViewCount(long videoId) {
        String key = "view_count:" + videoId;
        redis.opsForValue().increment(key);

        // Mark as dirty for background sync
        redis.opsForSet().add("dirty_view_counts", String.valueOf(videoId));
    }

    // Background job: Flush to MySQL every 5 minutes
    @Scheduled(fixedRate = 300000)
    public void flushToMySQL() {
        Set<String> dirtyIds = redis.opsForSet().members("dirty_view_counts");

        for (String videoIdStr : dirtyIds) {
            long videoId = Long.parseLong(videoIdStr);
            Long count = redis.opsForValue().get("view_count:" + videoId);

            // Atomic update in MySQL
            videoStatsRepository.updateViewCount(videoId, count);
        }

        redis.delete("dirty_view_counts");
    }
}
```

### Async Write Pattern

```java
@Service
public class VideoUploadService {

    @Async
    public CompletableFuture<Video> processUpload(UploadRequest request) {
        // Step 1: Write to primary (synchronous, returns quickly)
        Video video = videoRepository.save(Video.builder()
            .id(idGenerator.nextId())
            .channelId(request.getChannelId())
            .title(request.getTitle())
            .status(VideoStatus.PROCESSING)
            .build());

        // Step 2: Publish event for async processing
        kafkaTemplate.send("video-uploads", VideoUploadEvent.builder()
            .videoId(video.getId())
            .sourceUrl(request.getSourceUrl())
            .build());

        return CompletableFuture.completedFuture(video);
    }
}
```

---

## 9. Read Path Optimization

### Prepared Statement Caching

```java
@Configuration
public class DataSourceConfig {

    @Bean
    public HikariDataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://vtgate:3306/youtube_videos");

        // Connection pool settings
        config.setMaximumPoolSize(50);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(5000);

        // Prepared statement cache (critical for performance)
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        return new HikariDataSource(config);
    }
}
```

### Query Result Caching

```java
@Repository
public class VideoRepository {

    @Cacheable(value = "videos", key = "#videoId", unless = "#result == null")
    public Optional<Video> findById(Long videoId) {
        return jdbcTemplate.queryForObject(
            "SELECT * FROM videos WHERE video_id = ?",
            new VideoRowMapper(),
            videoId
        );
    }

    @Cacheable(value = "channel_videos", key = "#channelId + ':' + #page")
    public List<Video> findByChannelId(Long channelId, int page, int size) {
        return jdbcTemplate.query(
            """
            SELECT * FROM videos
            WHERE channel_id = ? AND upload_status = 'PUBLISHED'
            ORDER BY published_at DESC
            LIMIT ? OFFSET ?
            """,
            new VideoRowMapper(),
            channelId, size, page * size
        );
    }
}
```

### Projection Queries

```java
// BAD: Fetching all columns when only title needed
@Query("SELECT v FROM Video v WHERE v.channelId = :channelId")
List<Video> findByChannelId(Long channelId);

// GOOD: Projection interface for specific fields
public interface VideoSummary {
    Long getVideoId();
    String getTitle();
    String getThumbnailUrl();
    Long getViewCount();
}

@Query("SELECT v.videoId as videoId, v.title as title, " +
       "v.thumbnailUrl as thumbnailUrl, s.viewCount as viewCount " +
       "FROM Video v JOIN VideoStats s ON v.videoId = s.videoId " +
       "WHERE v.channelId = :channelId")
List<VideoSummary> findSummariesByChannelId(Long channelId);
```

---

## 10. Monitoring & Observability

### Key Metrics

```yaml
# Vitess VTGate Metrics
vtgate_queries_total:
  labels: [keyspace, table, plan_type]
  description: Total queries by type

vtgate_query_latency_seconds:
  labels: [keyspace, table]
  description: Query latency histogram

vtgate_errors_total:
  labels: [keyspace, error_code]
  description: Error count by type

# MySQL Metrics (per shard)
mysql_connections_active:
  description: Active connections

mysql_queries_per_second:
  description: QPS by query type

mysql_replication_lag_seconds:
  description: Replica lag behind primary

mysql_buffer_pool_hit_ratio:
  description: InnoDB buffer pool efficiency
```

### Alerting Rules

```yaml
groups:
  - name: youtube_mysql_alerts
    rules:
      - alert: HighQueryLatency
        expr: histogram_quantile(0.99, vtgate_query_latency_seconds) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Query latency p99 > 500ms"

      - alert: ReplicationLag
        expr: mysql_replication_lag_seconds > 10
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Replication lag exceeds 10 seconds"

      - alert: ConnectionPoolExhausted
        expr: mysql_connections_active / mysql_connections_max > 0.9
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Connection pool nearly exhausted"
```

### Query Analytics

```sql
-- Slow query log analysis
SELECT
    digest_text,
    count_star AS total_executions,
    avg_timer_wait / 1000000000 AS avg_latency_ms,
    sum_rows_examined / count_star AS avg_rows_examined,
    sum_rows_sent / count_star AS avg_rows_sent
FROM performance_schema.events_statements_summary_by_digest
ORDER BY sum_timer_wait DESC
LIMIT 20;
```

---

## 11. Disaster Recovery

### Backup Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                     BACKUP HIERARCHY                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Continuous Binlog Backup (RPO: seconds)                 │   │
│  │  - Stream to GCS/S3                                      │   │
│  │  - Point-in-time recovery capability                     │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Hourly Incremental Backup (RPO: 1 hour)                 │   │
│  │  - XtraBackup incremental                                │   │
│  │  - Compressed, encrypted                                 │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Daily Full Backup (Retention: 30 days)                  │   │
│  │  - Complete database snapshot                            │   │
│  │  - Stored in multiple regions                            │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Weekly Archive (Retention: 1 year)                      │   │
│  │  - Cold storage (Glacier/Archive)                        │   │
│  │  - Compliance and legal holds                            │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Failover Process

```
Automatic Primary Failover (via Vitess Orchestrator):

1. Detect primary failure (heartbeat timeout: 5s)
   ┌─────────┐
   │ Primary │ ──X── FAILURE
   └─────────┘

2. Elect new primary (most up-to-date replica)
   ┌─────────────┐
   │  Replica 1  │ ◀── GTID: 1000 (elected)
   │  Replica 2  │     GTID: 999
   │  Replica 3  │     GTID: 998
   └─────────────┘

3. Reconfigure replication topology
   ┌─────────────┐
   │ New Primary │
   └──────┬──────┘
          │
   ┌──────┴──────┐
   │             │
 Replica 2   Replica 3

4. Update VTGate routing
   Total failover time: 10-30 seconds
```

### Cross-Region Recovery

```
Region A (Primary)          Region B (DR)
┌─────────────────┐        ┌─────────────────┐
│   VTGate (A)    │        │   VTGate (B)    │
└────────┬────────┘        └────────┬────────┘
         │                          │
         │    Semi-sync             │
         │    Replication           │
┌────────▼────────┐        ┌───────▼─────────┐
│  MySQL Primary  │──────▶│  MySQL Replica   │
│    (Region A)   │  ~50ms │   (Region B)    │
└─────────────────┘  lag   └─────────────────┘

Failover to Region B:
1. Stop writes to Region A
2. Wait for replication to catch up
3. Promote Region B replica to primary
4. Update DNS/Load balancer
5. Resume operations

RTO: 5-15 minutes
RPO: <1 minute (semi-sync) or 0 (sync with latency cost)
```

---

## 12. Production Checklist

### Pre-Launch Validation

```
□ Schema Review
  ├── All tables have appropriate primary keys
  ├── Indexes cover common query patterns
  ├── Foreign keys evaluated (often removed for scale)
  ├── Data types optimized (BIGINT vs INT, VARCHAR lengths)
  └── Sharding keys chosen correctly

□ Query Audit
  ├── No SELECT * in production code
  ├── All queries use indexes (EXPLAIN verified)
  ├── Pagination uses keyset, not OFFSET
  ├── Cross-shard queries minimized
  └── N+1 queries eliminated

□ Connection Management
  ├── Pool sizes tuned per service
  ├── Connection timeouts configured
  ├── Prepared statement caching enabled
  └── Connection leak detection enabled

□ Replication
  ├── Semi-sync replication enabled for durability
  ├── Replica lag monitoring configured
  ├── Read routing respects consistency requirements
  └── Failover tested and automated

□ Backup & Recovery
  ├── Backup schedule configured and tested
  ├── Point-in-time recovery validated
  ├── Cross-region backup replication
  └── Recovery runbook documented

□ Monitoring
  ├── Query latency dashboards
  ├── Connection pool utilization
  ├── Replication lag alerts
  ├── Disk space monitoring
  └── Slow query logging enabled

□ Security
  ├── Encryption at rest (TDE)
  ├── Encryption in transit (TLS)
  ├── Least-privilege access controls
  ├── Audit logging enabled
  └── SQL injection prevention verified
```

### Capacity Planning

```
Video Uploads Growth Model:
- Current: 500 hours/minute = 720,000 videos/day
- Growth: 15% YoY
- Year 5: 720K × 1.15^5 = 1.45M videos/day

Storage Requirements:
- Metadata per video: ~5KB
- Daily growth: 1.45M × 5KB = 7.25GB/day
- Annual growth: 2.6TB/year (metadata only)

Query Load:
- Reads: 10M QPS × 70% = 7M read QPS
- Writes: 10M QPS × 30% = 3M write QPS

Shard Planning:
- Target: 10K QPS per shard
- Required shards: 7M / 10K = 700 read shards
- With replicas: 700 / 3 = ~250 primary shards
```

---

## API Reference

### Video Endpoints

```
GET  /api/v1/videos/{videoId}         - Get video metadata
POST /api/v1/videos                    - Upload new video
PUT  /api/v1/videos/{videoId}         - Update video metadata
DELETE /api/v1/videos/{videoId}       - Delete video

GET  /api/v1/videos/{videoId}/stats   - Get view/like counts
POST /api/v1/videos/{videoId}/view    - Record view event

GET  /api/v1/channels/{channelId}/videos - List channel videos
```

### Social Endpoints

```
GET  /api/v1/videos/{videoId}/comments - List comments
POST /api/v1/videos/{videoId}/comments - Add comment
DELETE /api/v1/comments/{commentId}    - Delete comment

POST /api/v1/videos/{videoId}/like     - Like video
POST /api/v1/videos/{videoId}/dislike  - Dislike video
DELETE /api/v1/videos/{videoId}/rating - Remove rating
```

### User Endpoints

```
GET  /api/v1/users/me/subscriptions   - List subscriptions
POST /api/v1/channels/{channelId}/subscribe - Subscribe
DELETE /api/v1/channels/{channelId}/subscribe - Unsubscribe

GET  /api/v1/users/me/history         - Watch history
POST /api/v1/users/me/history         - Add to history
DELETE /api/v1/users/me/history       - Clear history
```

---

## References

- [Vitess: A Database Clustering System for Horizontal Scaling of MySQL](https://vitess.io/)
- [Scaling YouTube's MySQL Infrastructure](https://www.youtube.com/watch?v=5yDO-tmIoXY)
- [How We Scaled MySQL to Handle 1.5 Billion Users](https://engineering.fb.com/2021/07/22/data-infrastructure/mysql/)
- [Vitess VSchema Documentation](https://vitess.io/docs/reference/features/vschema/)
