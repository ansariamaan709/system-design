package com.youtube.sharding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Shard Router
 */
class ShardRouterTest {

    @Test
    void shouldRouteVideoToConsistentShard() {
        ShardRouter router = new ShardRouter(256, 128, 512, true);

        long videoId = 12345L;
        int shard1 = router.routeVideo(videoId);
        int shard2 = router.routeVideo(videoId);

        assertEquals(shard1, shard2, "Same video ID should route to same shard");
    }

    @Test
    void shouldDistributeVideosAcrossShards() {
        ShardRouter router = new ShardRouter(256, 128, 512, true);

        int[] shardCounts = new int[256];
        for (long i = 1; i <= 10000; i++) {
            int shard = router.routeVideo(i);
            assertTrue(shard >= 0 && shard < 256);
            shardCounts[shard]++;
        }

        // Check for reasonable distribution (no shard should be empty)
        int nonEmptyShards = 0;
        for (int count : shardCounts) {
            if (count > 0)
                nonEmptyShards++;
        }

        assertTrue(nonEmptyShards > 200, "Should have good distribution across shards");
    }

    @Test
    void shouldRouteToZeroWhenDisabled() {
        ShardRouter router = new ShardRouter(256, 128, 512, false);

        assertEquals(0, router.routeVideo(12345L));
        assertEquals(0, router.routeUser(12345L));
        assertEquals(0, router.routeSocial(12345L));
    }

    @Test
    void shouldClassifyQueriesCorrectly() {
        ShardRouter router = new ShardRouter(256, 128, 512, true);

        assertEquals(ShardRouter.QueryType.POINT_LOOKUP,
                router.classifyQuery("videos", true, false));
        assertEquals(ShardRouter.QueryType.SCATTER_GATHER,
                router.classifyQuery("videos", false, true));
        assertEquals(ShardRouter.QueryType.FULL_SCAN,
                router.classifyQuery("videos", false, false));
    }

    @Test
    void shouldGetShardKeyRange() {
        ShardRouter router = new ShardRouter(16, 8, 32, true);

        String range = router.getShardKeyRange(0, 16);
        assertEquals("00-10", range);

        range = router.getShardKeyRange(15, 16);
        assertEquals("f0-100", range);
    }

    @Test
    void shouldReturnAllShards() {
        ShardRouter router = new ShardRouter(256, 128, 512, true);

        int[] videoShards = router.getAllVideoShards();
        assertEquals(256, videoShards.length);

        int[] userShards = router.getAllUserShards();
        assertEquals(128, userShards.length);

        int[] socialShards = router.getAllSocialShards();
        assertEquals(512, socialShards.length);
    }
}
