package com.amazons3.repository;

import com.amazons3.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Account entity operations.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Find account by access key ID
     */
    Optional<Account> findByAccessKeyId(String accessKeyId);

    /**
     * Find account by email
     */
    Optional<Account> findByEmail(String email);

    /**
     * Check if access key exists
     */
    boolean existsByAccessKeyId(String accessKeyId);

    /**
     * Update storage usage atomically
     */
    @Modifying
    @Query("UPDATE Account a SET a.storageUsedBytes = a.storageUsedBytes + :delta WHERE a.accountId = :accountId")
    void updateStorageUsed(Long accountId, Long delta);

    /**
     * Get bucket count for account
     */
    @Query("SELECT COUNT(b) FROM Bucket b WHERE b.ownerAccountId = :accountId")
    long countBucketsByAccount(Long accountId);
}
