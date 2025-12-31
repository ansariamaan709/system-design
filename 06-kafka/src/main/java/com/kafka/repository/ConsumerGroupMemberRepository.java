package com.kafka.repository;

import com.kafka.entity.ConsumerGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsumerGroupMemberRepository extends JpaRepository<ConsumerGroupMember, UUID> {

    List<ConsumerGroupMember> findByGroupGroupId(String groupId);

    Optional<ConsumerGroupMember> findByGroupGroupIdAndMemberId(String groupId, String memberId);

    @Query("SELECT m FROM ConsumerGroupMember m WHERE m.group.groupId = :groupId AND m.clientId = :clientId")
    Optional<ConsumerGroupMember> findByGroupIdAndClientId(String groupId, String clientId);

    @Query("SELECT m FROM ConsumerGroupMember m WHERE m.group.groupId = :groupId AND m.lastHeartbeat < :threshold")
    List<ConsumerGroupMember> findStaleMembers(String groupId, Instant threshold);

    @Query("SELECT COUNT(m) FROM ConsumerGroupMember m WHERE m.group.groupId = :groupId")
    int countByGroupId(String groupId);

    @Modifying
    @Query("UPDATE ConsumerGroupMember m SET m.lastHeartbeat = :timestamp WHERE m.memberId = :memberId")
    int updateHeartbeat(String memberId, Instant timestamp);

    @Modifying
    @Query("DELETE FROM ConsumerGroupMember m WHERE m.group.groupId = :groupId")
    int deleteByGroupId(String groupId);

    @Modifying
    @Query("DELETE FROM ConsumerGroupMember m WHERE m.memberId = :memberId")
    int deleteByMemberId(String memberId);

    @Query("SELECT m.assignedPartitions FROM ConsumerGroupMember m WHERE m.group.groupId = :groupId AND m.memberId = :memberId")
    String getAssignment(String groupId, String memberId);

    @Modifying
    @Query("UPDATE ConsumerGroupMember m SET m.assignedPartitions = :assignment WHERE m.memberId = :memberId")
    int updateAssignment(String memberId, String assignment);
}
