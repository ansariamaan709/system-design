package com.youtube.sharding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsistencyManagerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private ConsistencyManager consistencyManager;

    @BeforeEach
    void setUp() {
        consistencyManager = new ConsistencyManager(redisTemplate);
    }

    @Test
    void shouldRecordWrite() {
        // Given
        String sessionId = "session-123";
        String gtid = "uuid:1";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        consistencyManager.recordWrite(sessionId, gtid);

        // Then
        verify(valueOperations).set(
                eq("consistency:session-123"),
                eq(gtid),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldCheckConsistency() {
        // Given
        String sessionId = "session-123";
        String lastGtid = "uuid:10";
        String currentGtid = "uuid:15";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consistency:session-123")).thenReturn(lastGtid);

        // When
        boolean isConsistent = consistencyManager.isConsistent(sessionId, currentGtid);

        // Then
        assertTrue(isConsistent);
    }

    @Test
    void shouldReturnTrueWhenNoSessionRecord() {
        // Given
        String sessionId = "new-session";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consistency:new-session")).thenReturn(null);

        // When
        boolean isConsistent = consistencyManager.isConsistent(sessionId, "uuid:1");

        // Then
        assertTrue(isConsistent);
    }

    @Test
    void shouldReturnFalseWhenNotConsistent() {
        // Given
        String sessionId = "session-123";
        String lastGtid = "uuid:20";
        String currentGtid = "uuid:15"; // Behind the last write

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consistency:session-123")).thenReturn(lastGtid);

        // When
        boolean isConsistent = consistencyManager.isConsistent(sessionId, currentGtid);

        // Then
        assertFalse(isConsistent);
    }

    @Test
    void shouldClearSession() {
        // Given
        String sessionId = "session-123";

        when(redisTemplate.delete("consistency:session-123")).thenReturn(true);

        // When
        consistencyManager.clearSession(sessionId);

        // Then
        verify(redisTemplate).delete("consistency:session-123");
    }

    @Test
    void shouldDetermineReplicaReadability() {
        // Given
        String sessionId = "session-123";
        String lastGtid = "uuid:5";
        String replicaGtid = "uuid:10";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consistency:session-123")).thenReturn(lastGtid);

        // When
        boolean canReadFromReplica = consistencyManager.canReadFromReplica(sessionId, replicaGtid);

        // Then - replica has caught up (10 > 5)
        assertTrue(canReadFromReplica);
    }

    @Test
    void shouldPreventStaleReplicaRead() {
        // Given
        String sessionId = "session-123";
        String lastGtid = "uuid:10";
        String replicaGtid = "uuid:5"; // Replica is behind

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("consistency:session-123")).thenReturn(lastGtid);

        // When
        boolean canReadFromReplica = consistencyManager.canReadFromReplica(sessionId, replicaGtid);

        // Then
        assertFalse(canReadFromReplica);
    }
}
