package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Account entity representing an S3 user/account.
 * Holds credentials and storage quota information.
 */
@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_access_key", columnList = "access_key_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "access_key_id", unique = true, nullable = false, length = 64)
    private String accessKeyId;

    @Column(name = "secret_access_key", nullable = false, length = 128)
    private String secretAccessKey;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "max_buckets")
    @Builder.Default
    private Integer maxBuckets = 100;

    @Column(name = "storage_quota_bytes")
    @Builder.Default
    private Long storageQuotaBytes = 5497558138880L; // 5TB

    @Column(name = "storage_used_bytes")
    @Builder.Default
    private Long storageUsedBytes = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Check if account has storage quota available
     */
    public boolean hasStorageQuota(long additionalBytes) {
        return storageUsedBytes + additionalBytes <= storageQuotaBytes;
    }

    /**
     * Get remaining storage quota in bytes
     */
    public long getRemainingQuota() {
        return storageQuotaBytes - storageUsedBytes;
    }

    public enum AccountStatus {
        ACTIVE,
        SUSPENDED,
        DELETED
    }
}
