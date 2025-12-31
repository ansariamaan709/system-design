-- =====================================================
-- Kafka Clone Database Schema
-- Distributed Event Streaming Platform
-- =====================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- =====================================================
-- BROKER METADATA
-- =====================================================

-- Broker registry
CREATE TABLE IF NOT EXISTS brokers (
    broker_id INTEGER PRIMARY KEY,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    rack VARCHAR(100),
    status VARCHAR(20) DEFAULT 'ONLINE',
    endpoints JSONB,
    version VARCHAR(50),
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_broker_host_port UNIQUE (host, port)
);

CREATE INDEX idx_brokers_status ON brokers(status);
CREATE INDEX idx_brokers_last_heartbeat ON brokers(last_heartbeat);

-- =====================================================
-- TOPIC METADATA
-- =====================================================

-- Topics
CREATE TABLE IF NOT EXISTS topics (
    topic_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    partition_count INTEGER NOT NULL DEFAULT 1,
    replication_factor INTEGER NOT NULL DEFAULT 1,
    min_insync_replicas INTEGER DEFAULT 1,
    retention_ms BIGINT DEFAULT 604800000,  -- 7 days
    retention_bytes BIGINT DEFAULT -1,       -- unlimited
    segment_bytes BIGINT DEFAULT 1073741824, -- 1GB
    segment_ms BIGINT DEFAULT 604800000,     -- 7 days
    cleanup_policy VARCHAR(20) DEFAULT 'delete', -- delete, compact, delete,compact
    compression_type VARCHAR(20) DEFAULT 'producer', -- none, gzip, snappy, lz4, zstd, producer
    max_message_bytes INTEGER DEFAULT 1048576, -- 1MB
    message_timestamp_type VARCHAR(20) DEFAULT 'CreateTime', -- CreateTime, LogAppendTime
    is_internal BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    config JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_topics_name ON topics(name);
CREATE INDEX idx_topics_status ON topics(status);
CREATE INDEX idx_topics_is_internal ON topics(is_internal);

-- Topic partitions
CREATE TABLE IF NOT EXISTS partitions (
    partition_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_id UUID NOT NULL REFERENCES topics(topic_id) ON DELETE CASCADE,
    partition_number INTEGER NOT NULL,
    leader_broker_id INTEGER REFERENCES brokers(broker_id),
    leader_epoch INTEGER DEFAULT 0,
    isr_broker_ids INTEGER[] DEFAULT '{}',  -- In-Sync Replicas
    replica_broker_ids INTEGER[] DEFAULT '{}', -- All replicas
    offline_replica_ids INTEGER[] DEFAULT '{}',
    
    -- Log state
    log_start_offset BIGINT DEFAULT 0,
    log_end_offset BIGINT DEFAULT 0,       -- LEO: next offset to be written
    high_watermark BIGINT DEFAULT 0,       -- HW: committed offset
    
    -- Segment info
    active_segment_base_offset BIGINT DEFAULT 0,
    segment_count INTEGER DEFAULT 1,
    log_size_bytes BIGINT DEFAULT 0,
    
    status VARCHAR(20) DEFAULT 'ONLINE',
    last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_topic_partition UNIQUE (topic_id, partition_number)
);

CREATE INDEX idx_partitions_topic ON partitions(topic_id);
CREATE INDEX idx_partitions_leader ON partitions(leader_broker_id);
CREATE INDEX idx_partitions_status ON partitions(status);

-- Partition replicas (detailed replica state)
CREATE TABLE IF NOT EXISTS partition_replicas (
    replica_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    partition_id UUID NOT NULL REFERENCES partitions(partition_id) ON DELETE CASCADE,
    broker_id INTEGER NOT NULL REFERENCES brokers(broker_id),
    is_leader BOOLEAN DEFAULT FALSE,
    is_in_sync BOOLEAN DEFAULT TRUE,
    log_end_offset BIGINT DEFAULT 0,
    log_start_offset BIGINT DEFAULT 0,
    last_fetch_time TIMESTAMP,
    last_caught_up_time TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_partition_broker_replica UNIQUE (partition_id, broker_id)
);

CREATE INDEX idx_replicas_partition ON partition_replicas(partition_id);
CREATE INDEX idx_replicas_broker ON partition_replicas(broker_id);
CREATE INDEX idx_replicas_in_sync ON partition_replicas(is_in_sync);

-- =====================================================
-- CONSUMER GROUPS
-- =====================================================

-- Consumer groups
CREATE TABLE IF NOT EXISTS consumer_groups (
    group_id VARCHAR(255) PRIMARY KEY,
    state VARCHAR(30) DEFAULT 'EMPTY', -- EMPTY, PREPARING_REBALANCE, COMPLETING_REBALANCE, STABLE, DEAD
    protocol_type VARCHAR(100) DEFAULT 'consumer',
    protocol VARCHAR(100),
    leader_member_id VARCHAR(255),
    generation_id INTEGER DEFAULT 0,
    coordinator_broker_id INTEGER REFERENCES brokers(broker_id),
    assignment_strategy VARCHAR(100) DEFAULT 'range', -- range, roundrobin, sticky, cooperative-sticky
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consumer_groups_state ON consumer_groups(state);
CREATE INDEX idx_consumer_groups_coordinator ON consumer_groups(coordinator_broker_id);

-- Consumer group members
CREATE TABLE IF NOT EXISTS consumer_group_members (
    member_id VARCHAR(255) PRIMARY KEY,
    group_id VARCHAR(255) NOT NULL REFERENCES consumer_groups(group_id) ON DELETE CASCADE,
    client_id VARCHAR(255),
    client_host VARCHAR(255),
    session_timeout_ms INTEGER DEFAULT 45000,
    rebalance_timeout_ms INTEGER DEFAULT 300000,
    heartbeat_interval_ms INTEGER DEFAULT 3000,
    subscribed_topics TEXT[], -- Topics this member subscribed to
    assigned_partitions JSONB DEFAULT '[]', -- [{topic, partitions: [0,1,2]}]
    metadata BYTEA, -- Member metadata for assignment
    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_members_group ON consumer_group_members(group_id);
CREATE INDEX idx_members_client ON consumer_group_members(client_id);
CREATE INDEX idx_members_heartbeat ON consumer_group_members(last_heartbeat);

-- Consumer offsets (stored in __consumer_offsets topic, but also in DB for durability)
CREATE TABLE IF NOT EXISTS consumer_offsets (
    offset_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id VARCHAR(255) NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER NOT NULL,
    committed_offset BIGINT NOT NULL,
    leader_epoch INTEGER,
    metadata VARCHAR(1000),
    commit_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expire_timestamp TIMESTAMP,
    
    CONSTRAINT uk_consumer_offset UNIQUE (group_id, topic_name, partition_number)
);

CREATE INDEX idx_offsets_group ON consumer_offsets(group_id);
CREATE INDEX idx_offsets_topic_partition ON consumer_offsets(topic_name, partition_number);
CREATE INDEX idx_offsets_expire ON consumer_offsets(expire_timestamp);

-- =====================================================
-- TRANSACTIONS (For exactly-once semantics)
-- =====================================================

-- Producer IDs for idempotence
CREATE TABLE IF NOT EXISTS producer_ids (
    producer_id BIGINT PRIMARY KEY,
    producer_epoch SMALLINT NOT NULL DEFAULT 0,
    transactional_id VARCHAR(255),
    coordinator_broker_id INTEGER REFERENCES brokers(broker_id),
    last_sequence_number INTEGER DEFAULT -1,
    last_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_producer_transactional ON producer_ids(transactional_id);

-- Transactions
CREATE TABLE IF NOT EXISTS transactions (
    transactional_id VARCHAR(255) PRIMARY KEY,
    producer_id BIGINT NOT NULL,
    producer_epoch SMALLINT NOT NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'EMPTY', 
    -- EMPTY, ONGOING, PREPARE_COMMIT, PREPARE_ABORT, COMPLETE_COMMIT, COMPLETE_ABORT, DEAD
    timeout_ms INTEGER DEFAULT 60000,
    transaction_start_time TIMESTAMP,
    last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    partitions_in_txn JSONB DEFAULT '[]', -- [{topic, partitions}]
    pending_offsets JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_state ON transactions(state);
CREATE INDEX idx_transactions_producer ON transactions(producer_id);
CREATE INDEX idx_transactions_update_time ON transactions(last_update_time);

-- Transaction markers (for partition log)
CREATE TABLE IF NOT EXISTS transaction_markers (
    marker_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transactional_id VARCHAR(255) NOT NULL,
    producer_id BIGINT NOT NULL,
    producer_epoch SMALLINT NOT NULL,
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER NOT NULL,
    marker_type VARCHAR(20) NOT NULL, -- COMMIT, ABORT
    coordinator_epoch INTEGER,
    offset BIGINT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_markers_txn ON transaction_markers(transactional_id);
CREATE INDEX idx_markers_topic_partition ON transaction_markers(topic_name, partition_number);

-- =====================================================
-- ACLs & SECURITY
-- =====================================================

-- Access Control Lists
CREATE TABLE IF NOT EXISTS acls (
    acl_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource_type VARCHAR(50) NOT NULL, -- TOPIC, GROUP, CLUSTER, TRANSACTIONAL_ID
    resource_name VARCHAR(255) NOT NULL,
    pattern_type VARCHAR(20) DEFAULT 'LITERAL', -- LITERAL, PREFIXED
    principal VARCHAR(255) NOT NULL, -- User:alice, Group:developers
    host VARCHAR(255) DEFAULT '*',
    operation VARCHAR(50) NOT NULL, -- READ, WRITE, CREATE, DELETE, ALTER, DESCRIBE, CLUSTER_ACTION, ALL
    permission_type VARCHAR(20) NOT NULL, -- ALLOW, DENY
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_acl UNIQUE (resource_type, resource_name, pattern_type, principal, host, operation)
);

CREATE INDEX idx_acls_resource ON acls(resource_type, resource_name);
CREATE INDEX idx_acls_principal ON acls(principal);

-- =====================================================
-- QUOTAS
-- =====================================================

-- Client quotas
CREATE TABLE IF NOT EXISTS quotas (
    quota_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    entity_type VARCHAR(50) NOT NULL, -- user, client-id, user + client-id
    entity_name VARCHAR(255),
    quota_type VARCHAR(50) NOT NULL, -- producer_byte_rate, consumer_byte_rate, request_percentage
    quota_value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_quota UNIQUE (entity_type, entity_name, quota_type)
);

CREATE INDEX idx_quotas_entity ON quotas(entity_type, entity_name);

-- =====================================================
-- METRICS & AUDIT
-- =====================================================

-- Topic metrics (aggregated)
CREATE TABLE IF NOT EXISTS topic_metrics (
    metric_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER,
    metric_time TIMESTAMP NOT NULL,
    messages_in_per_sec DOUBLE PRECISION,
    bytes_in_per_sec DOUBLE PRECISION,
    bytes_out_per_sec DOUBLE PRECISION,
    total_produce_requests BIGINT,
    total_fetch_requests BIGINT,
    failed_produce_requests BIGINT,
    failed_fetch_requests BIGINT,
    avg_produce_latency_ms DOUBLE PRECISION,
    avg_fetch_latency_ms DOUBLE PRECISION,
    
    CONSTRAINT uk_topic_metrics UNIQUE (topic_name, partition_number, metric_time)
);

CREATE INDEX idx_topic_metrics_name_time ON topic_metrics(topic_name, metric_time DESC);

-- Partition metrics (for lag monitoring)
CREATE TABLE IF NOT EXISTS partition_metrics (
    metric_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER NOT NULL,
    group_id VARCHAR(255),
    metric_time TIMESTAMP NOT NULL,
    log_end_offset BIGINT,
    consumer_offset BIGINT,
    lag BIGINT,
    
    CONSTRAINT uk_partition_metrics UNIQUE (topic_name, partition_number, group_id, metric_time)
);

CREATE INDEX idx_partition_metrics_lag ON partition_metrics(group_id, lag DESC);
CREATE INDEX idx_partition_metrics_time ON partition_metrics(metric_time DESC);

-- Audit log
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type VARCHAR(50) NOT NULL,
    resource_type VARCHAR(50),
    resource_name VARCHAR(255),
    principal VARCHAR(255),
    client_host VARCHAR(255),
    operation VARCHAR(50),
    result VARCHAR(20), -- SUCCESS, FAILURE
    details JSONB,
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_event_time ON audit_log(event_time DESC);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_name);
CREATE INDEX idx_audit_principal ON audit_log(principal);

-- =====================================================
-- MESSAGE LOG (For small deployments / testing)
-- In production, messages are in segment files
-- =====================================================

CREATE TABLE IF NOT EXISTS message_log (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    topic_name VARCHAR(255) NOT NULL,
    partition_number INTEGER NOT NULL,
    offset_value BIGINT NOT NULL,
    
    -- Message data
    key BYTEA,
    value BYTEA,
    headers JSONB DEFAULT '[]',
    
    -- Metadata
    timestamp BIGINT NOT NULL, -- CreateTime or LogAppendTime
    timestamp_type VARCHAR(20) DEFAULT 'CreateTime',
    producer_id BIGINT,
    producer_epoch SMALLINT,
    sequence_number INTEGER,
    is_transactional BOOLEAN DEFAULT FALSE,
    is_control_record BOOLEAN DEFAULT FALSE,
    
    -- Storage metadata
    compressed_size INTEGER,
    uncompressed_size INTEGER,
    compression_type VARCHAR(20),
    crc32 BIGINT,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_message_offset UNIQUE (topic_name, partition_number, offset_value)
);

-- Partition by topic for better performance
CREATE INDEX idx_message_topic_partition_offset ON message_log(topic_name, partition_number, offset_value);
CREATE INDEX idx_message_timestamp ON message_log(topic_name, partition_number, timestamp);
CREATE INDEX idx_message_producer ON message_log(producer_id, sequence_number);

-- =====================================================
-- INTERNAL TOPICS INITIALIZATION
-- =====================================================

-- Insert internal topics
INSERT INTO topics (name, partition_count, replication_factor, cleanup_policy, is_internal, retention_ms)
VALUES 
    ('__consumer_offsets', 50, 3, 'compact', TRUE, -1),
    ('__transaction_state', 50, 3, 'compact', TRUE, -1)
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- FUNCTIONS & TRIGGERS
-- =====================================================

-- Function to update topic timestamp
CREATE OR REPLACE FUNCTION update_topic_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for topic updates
DROP TRIGGER IF EXISTS trigger_topic_update ON topics;
CREATE TRIGGER trigger_topic_update
    BEFORE UPDATE ON topics
    FOR EACH ROW
    EXECUTE FUNCTION update_topic_timestamp();

-- Function to update partition last_modified
CREATE OR REPLACE FUNCTION update_partition_modified()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_modified = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_partition_modified ON partitions;
CREATE TRIGGER trigger_partition_modified
    BEFORE UPDATE ON partitions
    FOR EACH ROW
    EXECUTE FUNCTION update_partition_modified();

-- Function to update consumer group timestamp
CREATE OR REPLACE FUNCTION update_consumer_group_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_consumer_group_update ON consumer_groups;
CREATE TRIGGER trigger_consumer_group_update
    BEFORE UPDATE ON consumer_groups
    FOR EACH ROW
    EXECUTE FUNCTION update_consumer_group_timestamp();

-- =====================================================
-- VIEWS
-- =====================================================

-- View: Topic details with partition info
CREATE OR REPLACE VIEW v_topic_details AS
SELECT 
    t.name AS topic_name,
    t.partition_count,
    t.replication_factor,
    t.retention_ms,
    t.cleanup_policy,
    t.status AS topic_status,
    COUNT(p.partition_id) AS actual_partitions,
    SUM(p.log_size_bytes) AS total_size_bytes,
    MAX(p.log_end_offset) AS max_offset,
    t.created_at
FROM topics t
LEFT JOIN partitions p ON t.topic_id = p.topic_id
GROUP BY t.topic_id, t.name, t.partition_count, t.replication_factor, 
         t.retention_ms, t.cleanup_policy, t.status, t.created_at;

-- View: Consumer group lag
CREATE OR REPLACE VIEW v_consumer_group_lag AS
SELECT 
    cg.group_id,
    cg.state AS group_state,
    co.topic_name,
    co.partition_number,
    co.committed_offset,
    p.log_end_offset,
    (p.log_end_offset - co.committed_offset) AS lag
FROM consumer_groups cg
JOIN consumer_offsets co ON cg.group_id = co.group_id
JOIN topics t ON co.topic_name = t.name
JOIN partitions p ON t.topic_id = p.topic_id AND co.partition_number = p.partition_number;

-- View: Under-replicated partitions
CREATE OR REPLACE VIEW v_under_replicated_partitions AS
SELECT 
    t.name AS topic_name,
    p.partition_number,
    p.leader_broker_id,
    array_length(p.isr_broker_ids, 1) AS isr_count,
    array_length(p.replica_broker_ids, 1) AS replica_count,
    t.replication_factor AS expected_replicas
FROM partitions p
JOIN topics t ON p.topic_id = t.topic_id
WHERE array_length(p.isr_broker_ids, 1) < t.replication_factor;

-- View: Offline partitions
CREATE OR REPLACE VIEW v_offline_partitions AS
SELECT 
    t.name AS topic_name,
    p.partition_number,
    p.status,
    p.leader_broker_id,
    b.status AS broker_status
FROM partitions p
JOIN topics t ON p.topic_id = t.topic_id
LEFT JOIN brokers b ON p.leader_broker_id = b.broker_id
WHERE p.status = 'OFFLINE' OR p.leader_broker_id IS NULL OR b.status != 'ONLINE';

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_partitions_topic_number ON partitions(topic_id, partition_number);
CREATE INDEX IF NOT EXISTS idx_offsets_group_topic ON consumer_offsets(group_id, topic_name);
CREATE INDEX IF NOT EXISTS idx_message_log_topic_offset ON message_log(topic_name, partition_number, offset_value DESC);
