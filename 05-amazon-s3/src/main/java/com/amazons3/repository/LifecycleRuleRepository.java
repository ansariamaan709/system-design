package com.amazons3.repository;

import com.amazons3.entity.LifecycleRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LifecycleRule entity operations.
 */
@Repository
public interface LifecycleRuleRepository extends JpaRepository<LifecycleRule, Long> {

    /**
     * Find all rules for a bucket
     */
    List<LifecycleRule> findByBucketId(Long bucketId);

    /**
     * Find enabled rules for a bucket
     */
    List<LifecycleRule> findByBucketIdAndStatus(Long bucketId, LifecycleRule.RuleStatus status);

    /**
     * Delete all rules for a bucket
     */
    void deleteByBucketId(Long bucketId);

    /**
     * Find rules by name
     */
    List<LifecycleRule> findByBucketIdAndRuleName(Long bucketId, String ruleName);
}
