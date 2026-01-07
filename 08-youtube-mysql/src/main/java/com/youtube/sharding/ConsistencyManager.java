package com.youtube.sharding;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consistency Manager - Ensures read-your-writes consistency
 * 
 * Problem: After a user writes data, reading from a replica might
 * not see the recent write due to replication lag.
 * 
 * Solution: Track GTID (Global Transaction ID) per session and
 * route reads to primary until replica catches up.
 */
@Component
public class ConsistencyManager {

    // Track last write GTID per session
    private final Map<String, TransactionPosition> sessionPositions = new ConcurrentHashMap<>();

    // Simulated replica positions (in production, queried from MySQL)
    private volatile long primaryPosition = 0;
    private volatile long replicaPosition = 0;

    /**
     * Record a write operation for a session
     */
    public void recordWrite(String sessionId, long gtid) {
        sessionPositions.put(sessionId, new TransactionPosition(gtid, System.currentTimeMillis()));

        // Update primary position
        if (gtid > primaryPosition) {
            primaryPosition = gtid;
        }
    }

    /**
     * Determine the best target for a read operation
     */
    public QueryTarget selectReadTarget(String sessionId, boolean requireStrongConsistency) {
        // Strong consistency always goes to primary
        if (requireStrongConsistency) {
            return QueryTarget.PRIMARY;
        }

        TransactionPosition lastWrite = sessionPositions.get(sessionId);

        // No recent writes - safe to use replica
        if (lastWrite == null) {
            return QueryTarget.REPLICA;
        }

        // Check if replica has caught up
        if (replicaCaughtUp(lastWrite.gtid())) {
            return QueryTarget.REPLICA;
        }

        // Check if write is recent (within consistency window)
        long writeAge = System.currentTimeMillis() - lastWrite.timestamp();
        if (writeAge > 5000) { // 5 second consistency window
            // Old write - assume replica caught up, clear session state
            sessionPositions.remove(sessionId);
            return QueryTarget.REPLICA;
        }

        // Recent write, replica hasn't caught up - route to primary
        return QueryTarget.PRIMARY;
    }

    /**
     * Check if replica has caught up to a specific GTID
     */
    public boolean replicaCaughtUp(long targetGtid) {
        return replicaPosition >= targetGtid;
    }

    /**
     * Update replica position (called by replication monitor)
     */
    public void updateReplicaPosition(long position) {
        this.replicaPosition = position;
    }

    /**
     * Get current replication lag in transactions
     */
    public long getReplicationLag() {
        return primaryPosition - replicaPosition;
    }

    /**
     * Clear session state (on logout or session expiry)
     */
    public void clearSession(String sessionId) {
        sessionPositions.remove(sessionId);
    }

    /**
     * Cleanup old session entries (periodic maintenance)
     */
    public void cleanupOldSessions(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        sessionPositions.entrySet().removeIf(entry -> entry.getValue().timestamp() < cutoff);
    }

    public enum QueryTarget {
        PRIMARY,
        REPLICA
    }

    private record TransactionPosition(long gtid, long timestamp) {
    }
}
