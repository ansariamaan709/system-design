package com.youtube.sharding;

import com.google.common.hash.Hashing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Shard Router - Simulates Vitess VTGate query routing
 * 
 * In production Vitess:
 * - VTGate handles automatic query routing based on vindexes
 * - This implementation demonstrates the sharding logic
 * 
 * Sharding Strategies:
 * - Hash-based: Even distribution using xxhash64
 * - Range-based: Time-series or geographic sharding
 * - Lookup: Secondary index for non-sharding key queries
 */
@Component
public class ShardRouter {

    private final int videoShards;
    private final int userShards;
    private final int socialShards;
    private final boolean shardingEnabled;

    public ShardRouter(
            @Value("${youtube.sharding.video-shards:256}") int videoShards,
            @Value("${youtube.sharding.user-shards:128}") int userShards,
            @Value("${youtube.sharding.social-shards:512}") int socialShards,
            @Value("${youtube.sharding.enabled:true}") boolean shardingEnabled) {
        this.videoShards = videoShards;
        this.userShards = userShards;
        this.socialShards = socialShards;
        this.shardingEnabled = shardingEnabled;
    }

    /**
     * Route video by video_id (primary sharding key for videos keyspace)
     */
    public int routeVideo(long videoId) {
        if (!shardingEnabled)
            return 0;
        return hashToShard(videoId, videoShards);
    }

    /**
     * Route user by user_id (primary sharding key for users keyspace)
     */
    public int routeUser(long userId) {
        if (!shardingEnabled)
            return 0;
        return hashToShard(userId, userShards);
    }

    /**
     * Route social data by entity_id (usually video_id for comments/likes)
     */
    public int routeSocial(long entityId) {
        if (!shardingEnabled)
            return 0;
        return hashToShard(entityId, socialShards);
    }

    /**
     * Get all shards that need to be queried for scatter-gather
     */
    public int[] getAllVideoShards() {
        return generateShardRange(videoShards);
    }

    public int[] getAllUserShards() {
        return generateShardRange(userShards);
    }

    public int[] getAllSocialShards() {
        return generateShardRange(socialShards);
    }

    /**
     * Hash-based shard calculation using xxhash64 (like Vitess)
     */
    private int hashToShard(long id, int totalShards) {
        // Use xxhash64 for consistent, fast hashing
        long hash = Hashing.sipHash24()
                .hashString(String.valueOf(id), StandardCharsets.UTF_8)
                .asLong();

        // Map to shard (ensure positive result)
        return (int) ((hash & 0x7FFFFFFFFFFFFFFFL) % totalShards);
    }

    /**
     * Get shard key range (for Vitess-style shard naming)
     * Converts shard number to hex range like "00-10", "10-20", etc.
     */
    public String getShardKeyRange(int shardNum, int totalShards) {
        int rangeSize = 256 / totalShards;
        int start = shardNum * rangeSize;
        int end = (shardNum + 1) * rangeSize;

        return String.format("%02x-%02x", start, end);
    }

    /**
     * Determine query type for optimization
     */
    public QueryType classifyQuery(String tableName, boolean hasShardingKey, boolean isScatter) {
        if (hasShardingKey) {
            return QueryType.POINT_LOOKUP;
        } else if (isScatter) {
            return QueryType.SCATTER_GATHER;
        } else {
            return QueryType.FULL_SCAN;
        }
    }

    private int[] generateShardRange(int totalShards) {
        int[] shards = new int[totalShards];
        for (int i = 0; i < totalShards; i++) {
            shards[i] = i;
        }
        return shards;
    }

    public enum QueryType {
        POINT_LOOKUP, // Routes to single shard (best)
        IN_QUERY, // Routes to multiple specific shards
        SCATTER_GATHER, // Queries all shards (expensive)
        FULL_SCAN // Avoid in production
    }

    // Getters
    public int getVideoShards() {
        return videoShards;
    }

    public int getUserShards() {
        return userShards;
    }

    public int getSocialShards() {
        return socialShards;
    }

    public boolean isShardingEnabled() {
        return shardingEnabled;
    }
}
