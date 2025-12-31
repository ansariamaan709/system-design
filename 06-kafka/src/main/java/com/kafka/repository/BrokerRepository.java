package com.kafka.repository;

import com.kafka.entity.Broker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface BrokerRepository extends JpaRepository<Broker, Integer> {

    Optional<Broker> findByHostAndPort(String host, int port);

    List<Broker> findByStatus(Broker.BrokerStatus status);

    @Query("SELECT b FROM Broker b WHERE b.status = 'ONLINE'")
    List<Broker> findOnlineBrokers();

    @Query("SELECT b FROM Broker b WHERE b.lastHeartbeat < :threshold AND b.status = 'ONLINE'")
    List<Broker> findStaleBrokers(Instant threshold);

    @Query("SELECT b FROM Broker b WHERE b.isController = true")
    Optional<Broker> findController();

    @Query("SELECT COUNT(b) FROM Broker b WHERE b.status = 'ONLINE'")
    int countOnlineBrokers();

    @Modifying
    @Query("UPDATE Broker b SET b.lastHeartbeat = :timestamp WHERE b.brokerId = :brokerId")
    int updateHeartbeat(Integer brokerId, Instant timestamp);

    @Modifying
    @Query("UPDATE Broker b SET b.status = :status WHERE b.brokerId = :brokerId")
    int updateStatus(Integer brokerId, Broker.BrokerStatus status);

    @Modifying
    @Query("UPDATE Broker b SET b.isController = false WHERE b.isController = true")
    int clearController();

    @Modifying
    @Query("UPDATE Broker b SET b.isController = true, b.controllerEpoch = b.controllerEpoch + 1 " +
            "WHERE b.brokerId = :brokerId")
    int setController(Integer brokerId);

    @Query("SELECT b.rack FROM Broker b WHERE b.status = 'ONLINE' GROUP BY b.rack")
    List<String> findDistinctRacks();

    @Query("SELECT b FROM Broker b WHERE b.rack = :rack AND b.status = 'ONLINE'")
    List<Broker> findByRackAndOnline(String rack);
}
