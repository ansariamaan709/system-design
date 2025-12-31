package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * LifecycleRule entity - defines object lifecycle transitions and expirations.
 */
@Entity
@Table(name = "lifecycle_rules", indexes = {
        @Index(name = "idx_lifecycle_rules_bucket", columnList = "bucket_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LifecycleRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "bucket_id", nullable = false)
    private Long bucketId;

    @Column(name = "rule_name", nullable = false)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private RuleStatus status = RuleStatus.ENABLED;

    @Column(name = "prefix", length = 1024)
    private String prefix;

    // Expiration
    @Column(name = "expiration_days")
    private Integer expirationDays;

    @Column(name = "expiration_date")
    private Instant expirationDate;

    @Column(name = "expired_object_delete_marker")
    @Builder.Default
    private Boolean expiredObjectDeleteMarker = false;

    // Transitions
    @Column(name = "transition_days")
    private Integer transitionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_storage_class", length = 30)
    private Bucket.StorageClass transitionStorageClass;

    // Noncurrent version handling
    @Column(name = "noncurrent_expiration_days")
    private Integer noncurrentExpirationDays;

    @Column(name = "noncurrent_transition_days")
    private Integer noncurrentTransitionDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "noncurrent_transition_storage_class", length = 30)
    private Bucket.StorageClass noncurrentTransitionStorageClass;

    // Abort incomplete multipart
    @Column(name = "abort_incomplete_days")
    private Integer abortIncompleteDays;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum RuleStatus {
        ENABLED,
        DISABLED
    }
}
