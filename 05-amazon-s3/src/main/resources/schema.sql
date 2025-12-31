-- Amazon S3 Clone Database Schema
-- Designed for high-performance object storage metadata

-- =============================================================================
-- ACCOUNTS & AUTHENTICATION
-- =============================================================================

CREATE TABLE IF NOT EXISTS accounts (
    account_id BIGSERIAL PRIMARY KEY,
    access_key_id VARCHAR(64) UNIQUE NOT NULL,
    secret_access_key VARCHAR(128) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    max_buckets INT DEFAULT 100,
    storage_quota_bytes BIGINT DEFAULT 5497558138880, -- 5TB default
    storage_used_bytes BIGINT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_accounts_access_key ON accounts(access_key_id);

-- =============================================================================
-- BUCKETS
-- =============================================================================

CREATE TABLE IF NOT EXISTS buckets (
    bucket_id BIGSERIAL PRIMARY KEY,
    bucket_name VARCHAR(63) UNIQUE NOT NULL,
    owner_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    region VARCHAR(50) DEFAULT 'us-east-1',
    
    -- Versioning
    versioning_status VARCHAR(20) DEFAULT 'DISABLED', -- DISABLED, ENABLED, SUSPENDED
    
    -- Object Lock (WORM compliance)
    object_lock_enabled BOOLEAN DEFAULT FALSE,
    default_retention_mode VARCHAR(20), -- GOVERNANCE, COMPLIANCE
    default_retention_days INT,
    
    -- Storage class
    default_storage_class VARCHAR(30) DEFAULT 'STANDARD',
    
    -- Statistics
    object_count BIGINT DEFAULT 0,
    total_size_bytes BIGINT DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_buckets_owner ON buckets(owner_account_id);
CREATE INDEX idx_buckets_name ON buckets(bucket_name);

-- =============================================================================
-- BUCKET CONFIGURATIONS
-- =============================================================================

-- Bucket ACL
CREATE TABLE IF NOT EXISTS bucket_acls (
    acl_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    grantee_type VARCHAR(20) NOT NULL, -- CANONICAL_USER, GROUP, EMAIL
    grantee_id VARCHAR(255) NOT NULL,
    permission VARCHAR(20) NOT NULL, -- FULL_CONTROL, WRITE, READ, WRITE_ACP, READ_ACP
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bucket_acls_bucket ON bucket_acls(bucket_id);

-- Bucket Policy (JSON document)
CREATE TABLE IF NOT EXISTS bucket_policies (
    policy_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT UNIQUE NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    policy_document JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- CORS Configuration
CREATE TABLE IF NOT EXISTS bucket_cors (
    cors_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    allowed_origins TEXT[] NOT NULL,
    allowed_methods TEXT[] NOT NULL,
    allowed_headers TEXT[],
    expose_headers TEXT[],
    max_age_seconds INT DEFAULT 3600,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bucket_cors_bucket ON bucket_cors(bucket_id);

-- Lifecycle Rules
CREATE TABLE IF NOT EXISTS lifecycle_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'ENABLED',
    prefix VARCHAR(1024),
    
    -- Expiration
    expiration_days INT,
    expiration_date TIMESTAMP WITH TIME ZONE,
    expired_object_delete_marker BOOLEAN DEFAULT FALSE,
    
    -- Transitions
    transition_days INT,
    transition_storage_class VARCHAR(30),
    
    -- Noncurrent versions
    noncurrent_expiration_days INT,
    noncurrent_transition_days INT,
    noncurrent_transition_storage_class VARCHAR(30),
    
    -- Abort incomplete multipart
    abort_incomplete_days INT,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lifecycle_rules_bucket ON lifecycle_rules(bucket_id);

-- =============================================================================
-- OBJECTS
-- =============================================================================

CREATE TABLE IF NOT EXISTS objects (
    object_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    object_key VARCHAR(1024) NOT NULL,
    
    -- Versioning
    version_id VARCHAR(64) NOT NULL DEFAULT 'null',
    is_latest BOOLEAN DEFAULT TRUE,
    is_delete_marker BOOLEAN DEFAULT FALSE,
    
    -- Content
    etag VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(255) DEFAULT 'application/octet-stream',
    content_encoding VARCHAR(50),
    content_disposition VARCHAR(255),
    content_language VARCHAR(50),
    cache_control VARCHAR(255),
    
    -- Storage
    storage_class VARCHAR(30) DEFAULT 'STANDARD',
    storage_path VARCHAR(1024) NOT NULL, -- Physical storage location
    
    -- Checksums
    checksum_sha256 VARCHAR(64),
    checksum_crc32 VARCHAR(16),
    
    -- Object Lock
    lock_mode VARCHAR(20), -- GOVERNANCE, COMPLIANCE
    lock_retain_until TIMESTAMP WITH TIME ZONE,
    legal_hold BOOLEAN DEFAULT FALSE,
    
    -- Encryption
    sse_algorithm VARCHAR(20), -- AES256, aws:kms
    kms_key_id VARCHAR(255),
    
    -- Timestamps
    last_modified TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    -- Unique constraint for bucket + key + version
    UNIQUE(bucket_id, object_key, version_id)
);

CREATE INDEX idx_objects_bucket_key ON objects(bucket_id, object_key);
CREATE INDEX idx_objects_bucket_prefix ON objects(bucket_id, object_key varchar_pattern_ops);
CREATE INDEX idx_objects_latest ON objects(bucket_id, is_latest) WHERE is_latest = TRUE;
CREATE INDEX idx_objects_storage_path ON objects(storage_path);
CREATE INDEX idx_objects_expires ON objects(expires_at) WHERE expires_at IS NOT NULL;

-- Object Metadata (custom user metadata)
CREATE TABLE IF NOT EXISTS object_metadata (
    metadata_id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES objects(object_id) ON DELETE CASCADE,
    meta_key VARCHAR(255) NOT NULL,
    meta_value TEXT,
    UNIQUE(object_id, meta_key)
);

CREATE INDEX idx_object_metadata_object ON object_metadata(object_id);

-- Object ACL
CREATE TABLE IF NOT EXISTS object_acls (
    acl_id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES objects(object_id) ON DELETE CASCADE,
    grantee_type VARCHAR(20) NOT NULL,
    grantee_id VARCHAR(255) NOT NULL,
    permission VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_object_acls_object ON object_acls(object_id);

-- Object Tags
CREATE TABLE IF NOT EXISTS object_tags (
    tag_id BIGSERIAL PRIMARY KEY,
    object_id BIGINT NOT NULL REFERENCES objects(object_id) ON DELETE CASCADE,
    tag_key VARCHAR(128) NOT NULL,
    tag_value VARCHAR(256),
    UNIQUE(object_id, tag_key)
);

CREATE INDEX idx_object_tags_object ON object_tags(object_id);

-- =============================================================================
-- MULTIPART UPLOADS
-- =============================================================================

CREATE TABLE IF NOT EXISTS multipart_uploads (
    upload_id VARCHAR(64) PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    object_key VARCHAR(1024) NOT NULL,
    initiator_account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    
    -- Content info
    content_type VARCHAR(255) DEFAULT 'application/octet-stream',
    storage_class VARCHAR(30) DEFAULT 'STANDARD',
    
    -- Encryption
    sse_algorithm VARCHAR(20),
    kms_key_id VARCHAR(255),
    
    -- Status
    status VARCHAR(20) DEFAULT 'IN_PROGRESS', -- IN_PROGRESS, COMPLETED, ABORTED
    
    -- Timestamps
    initiated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_multipart_bucket_key ON multipart_uploads(bucket_id, object_key);
CREATE INDEX idx_multipart_status ON multipart_uploads(status);
CREATE INDEX idx_multipart_expires ON multipart_uploads(expires_at) WHERE status = 'IN_PROGRESS';

-- Multipart Upload Parts
CREATE TABLE IF NOT EXISTS multipart_parts (
    part_id BIGSERIAL PRIMARY KEY,
    upload_id VARCHAR(64) NOT NULL REFERENCES multipart_uploads(upload_id) ON DELETE CASCADE,
    part_number INT NOT NULL,
    etag VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(1024) NOT NULL,
    checksum_sha256 VARCHAR(64),
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(upload_id, part_number)
);

CREATE INDEX idx_multipart_parts_upload ON multipart_parts(upload_id);

-- Multipart Metadata
CREATE TABLE IF NOT EXISTS multipart_metadata (
    metadata_id BIGSERIAL PRIMARY KEY,
    upload_id VARCHAR(64) NOT NULL REFERENCES multipart_uploads(upload_id) ON DELETE CASCADE,
    meta_key VARCHAR(255) NOT NULL,
    meta_value TEXT,
    UNIQUE(upload_id, meta_key)
);

-- =============================================================================
-- EVENT NOTIFICATIONS
-- =============================================================================

CREATE TABLE IF NOT EXISTS bucket_notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    
    -- Events to trigger on
    events TEXT[] NOT NULL, -- s3:ObjectCreated:*, s3:ObjectRemoved:*, etc.
    
    -- Filter rules
    prefix_filter VARCHAR(1024),
    suffix_filter VARCHAR(255),
    
    -- Destination
    destination_type VARCHAR(20) NOT NULL, -- KAFKA, WEBHOOK, SQS, SNS
    destination_arn VARCHAR(1024) NOT NULL,
    
    status VARCHAR(20) DEFAULT 'ENABLED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_bucket_notifications_bucket ON bucket_notifications(bucket_id);

-- =============================================================================
-- ACCESS LOGS
-- =============================================================================

CREATE TABLE IF NOT EXISTS access_logs (
    log_id BIGSERIAL PRIMARY KEY,
    bucket_name VARCHAR(63) NOT NULL,
    request_time TIMESTAMP WITH TIME ZONE NOT NULL,
    remote_ip VARCHAR(45),
    requester_account_id BIGINT,
    request_id VARCHAR(64) NOT NULL,
    operation VARCHAR(50) NOT NULL,
    object_key VARCHAR(1024),
    request_uri TEXT,
    http_status INT,
    error_code VARCHAR(50),
    bytes_sent BIGINT,
    object_size BIGINT,
    total_time_ms INT,
    turnaround_time_ms INT,
    referrer TEXT,
    user_agent TEXT,
    version_id VARCHAR(64)
);

CREATE INDEX idx_access_logs_bucket_time ON access_logs(bucket_name, request_time DESC);
CREATE INDEX idx_access_logs_request_id ON access_logs(request_id);

-- =============================================================================
-- REPLICATION
-- =============================================================================

CREATE TABLE IF NOT EXISTS replication_rules (
    rule_id BIGSERIAL PRIMARY KEY,
    source_bucket_id BIGINT NOT NULL REFERENCES buckets(bucket_id) ON DELETE CASCADE,
    rule_name VARCHAR(255) NOT NULL,
    status VARCHAR(20) DEFAULT 'ENABLED',
    priority INT DEFAULT 0,
    
    -- Filter
    prefix VARCHAR(1024),
    
    -- Destination
    destination_bucket VARCHAR(255) NOT NULL,
    destination_region VARCHAR(50),
    destination_storage_class VARCHAR(30),
    
    -- Options
    replicate_delete_markers BOOLEAN DEFAULT FALSE,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_replication_rules_bucket ON replication_rules(source_bucket_id);

-- =============================================================================
-- FUNCTIONS AND TRIGGERS
-- =============================================================================

-- Function to update bucket statistics
CREATE OR REPLACE FUNCTION update_bucket_stats()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE buckets 
        SET object_count = object_count + 1,
            total_size_bytes = total_size_bytes + NEW.size_bytes,
            updated_at = CURRENT_TIMESTAMP
        WHERE bucket_id = NEW.bucket_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE buckets 
        SET object_count = object_count - 1,
            total_size_bytes = total_size_bytes - OLD.size_bytes,
            updated_at = CURRENT_TIMESTAMP
        WHERE bucket_id = OLD.bucket_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger for bucket stats
DROP TRIGGER IF EXISTS trigger_bucket_stats ON objects;
CREATE TRIGGER trigger_bucket_stats
AFTER INSERT OR DELETE ON objects
FOR EACH ROW EXECUTE FUNCTION update_bucket_stats();

-- Function to update account storage usage
CREATE OR REPLACE FUNCTION update_account_storage()
RETURNS TRIGGER AS $$
DECLARE
    v_account_id BIGINT;
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT owner_account_id INTO v_account_id FROM buckets WHERE bucket_id = NEW.bucket_id;
        UPDATE accounts 
        SET storage_used_bytes = storage_used_bytes + NEW.size_bytes,
            updated_at = CURRENT_TIMESTAMP
        WHERE account_id = v_account_id;
    ELSIF TG_OP = 'DELETE' THEN
        SELECT owner_account_id INTO v_account_id FROM buckets WHERE bucket_id = OLD.bucket_id;
        UPDATE accounts 
        SET storage_used_bytes = storage_used_bytes - OLD.size_bytes,
            updated_at = CURRENT_TIMESTAMP
        WHERE account_id = v_account_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- Trigger for account storage
DROP TRIGGER IF EXISTS trigger_account_storage ON objects;
CREATE TRIGGER trigger_account_storage
AFTER INSERT OR DELETE ON objects
FOR EACH ROW EXECUTE FUNCTION update_account_storage();

-- =============================================================================
-- SEED DATA
-- =============================================================================

-- Default test account
INSERT INTO accounts (access_key_id, secret_access_key, account_name, email)
VALUES ('AKIAIOSFODNN7EXAMPLE', 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY', 'Test Account', 'test@example.com')
ON CONFLICT (access_key_id) DO NOTHING;
