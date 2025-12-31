package com.stockexchange.repository;

import com.stockexchange.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByApiKey(String apiKey);

    Optional<Client> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByApiKey(String apiKey);

    List<Client> findByStatus(Client.ClientStatus status);

    @Query("SELECT c FROM Client c WHERE c.clientType = :type AND c.status = 'ACTIVE'")
    List<Client> findByClientType(@Param("type") Client.ClientType type);

    // Find market makers
    @Query("SELECT c FROM Client c WHERE c.isMarketMaker = true AND c.status = 'ACTIVE'")
    List<Client> findActiveMarketMakers();

    // Authenticate client
    @Query("SELECT c FROM Client c WHERE c.apiKey = :apiKey AND c.apiSecret = :apiSecret " +
            "AND c.status = 'ACTIVE'")
    Optional<Client> authenticate(@Param("apiKey") String apiKey, @Param("apiSecret") String apiSecret);

    // Update last login
    @Modifying
    @Query("UPDATE Client c SET c.lastLoginAt = :timestamp WHERE c.clientId = :clientId")
    int updateLastLogin(@Param("clientId") Long clientId, @Param("timestamp") LocalDateTime timestamp);

    // Check if client has DMA access
    @Query("SELECT c.hasDmaAccess FROM Client c WHERE c.clientId = :clientId")
    Boolean hasDmaAccess(@Param("clientId") Long clientId);

    // Get rate limit for client
    @Query("SELECT c.rateLimitPerSecond FROM Client c WHERE c.clientId = :clientId")
    Integer getRateLimit(@Param("clientId") Long clientId);

    // Find clients with active sessions
    @Query("SELECT c FROM Client c WHERE c.lastLoginAt > :since AND c.status = 'ACTIVE'")
    List<Client> findRecentlyActiveClients(@Param("since") LocalDateTime since);

    // Statistics
    @Query("SELECT c.clientType, COUNT(c) FROM Client c GROUP BY c.clientType")
    List<Object[]> getClientCountByType();
}
