package com.kafka.controller;

import com.kafka.service.ConsumerService;
import com.kafka.service.ConsumerService.*;
import com.kafka.service.GroupCoordinator;
import com.kafka.storage.Record;
import com.kafka.storage.RecordBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Consumer Controller - REST API for consuming messages from Kafka.
 * 
 * Endpoints:
 * - Subscribe to topics
 * - Fetch messages
 * - Commit offsets
 * - Consumer group management
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/consumer")
@RequiredArgsConstructor
public class ConsumerController {

        private final ConsumerService consumerService;
        private final GroupCoordinator groupCoordinator;

        // ==================== Subscription ====================

        /**
         * Subscribe to topics.
         */
        @PostMapping("/groups/{groupId}/subscribe")
        public ResponseEntity<SubscriptionResponse> subscribe(
                        @PathVariable String groupId,
                        @RequestBody SubscribeRequest request) {

                log.info("Consumer {} subscribing to {} in group {}",
                                request.memberId(), request.topics(), groupId);

                SubscriptionResult result = consumerService.subscribe(
                                groupId,
                                request.memberId(),
                                request.clientId(),
                                request.topics(),
                                request.assignmentStrategy());

                return ResponseEntity.ok(new SubscriptionResponse(
                                result.groupId(),
                                result.memberId(),
                                result.generation(),
                                result.state()));
        }

        /**
         * Leave a consumer group.
         */
        @PostMapping("/groups/{groupId}/leave")
        public ResponseEntity<Void> leaveGroup(
                        @PathVariable String groupId,
                        @RequestBody LeaveGroupRequest request) {

                consumerService.leaveGroup(groupId, request.memberId());
                return ResponseEntity.ok().build();
        }

        // ==================== Fetch Messages ====================

        /**
         * Fetch messages from subscribed partitions.
         */
        @PostMapping("/groups/{groupId}/fetch")
        public ResponseEntity<FetchResponse> fetch(
                        @PathVariable String groupId,
                        @RequestBody FetchRequest request) throws IOException {

                Map<TopicPartition, Long> fetchOffsets = new HashMap<>();
                for (PartitionOffset po : request.offsets()) {
                        fetchOffsets.put(new TopicPartition(po.topic(), po.partition()), po.offset());
                }

                FetchResult result = consumerService.fetch(
                                groupId,
                                request.memberId(),
                                fetchOffsets,
                                request.maxBytes() != null ? request.maxBytes() : 1048576,
                                request.maxWaitMs() != null ? request.maxWaitMs() : 500);

                List<PartitionData> partitionData = new ArrayList<>();
                for (FetchPartitionResult fpr : result.results()) {
                        List<RecordData> records = new ArrayList<>();

                        if (fpr.batches() != null) {
                                for (RecordBatch batch : fpr.batches()) {
                                        for (Record record : batch.getRecords()) {
                                                records.add(new RecordData(
                                                                record.getOffset(),
                                                                record.getTimestamp(),
                                                                record.getKey() != null
                                                                                ? new String(record.getKey(),
                                                                                                StandardCharsets.UTF_8)
                                                                                : null,
                                                                record.getValue() != null
                                                                                ? new String(record.getValue(),
                                                                                                StandardCharsets.UTF_8)
                                                                                : null,
                                                                record.getHeaders() != null
                                                                                ? record.getHeaders().stream()
                                                                                                .collect(Collectors
                                                                                                                .toMap(
                                                                                                                                Record.Header::getKey,
                                                                                                                                h -> new String(h
                                                                                                                                                .getValue(),
                                                                                                                                                StandardCharsets.UTF_8)))
                                                                                : null));
                                        }
                                }
                        }

                        partitionData.add(new PartitionData(
                                        fpr.topic(),
                                        fpr.partition(),
                                        fpr.errorMessage(),
                                        fpr.highWatermark(),
                                        fpr.logEndOffset(),
                                        records));
                }

                return ResponseEntity.ok(new FetchResponse(partitionData));
        }

        // ==================== Offset Management ====================

        /**
         * Commit offsets.
         */
        @PostMapping("/groups/{groupId}/offsets/commit")
        public ResponseEntity<Void> commitOffsets(
                        @PathVariable String groupId,
                        @RequestBody CommitOffsetsRequest request) {

                Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
                for (OffsetCommitData ocd : request.offsets()) {
                        offsets.put(
                                        new TopicPartition(ocd.topic(), ocd.partition()),
                                        new OffsetAndMetadata(ocd.offset(), ocd.leaderEpoch(), ocd.metadata()));
                }

                consumerService.commitOffsets(groupId, request.memberId(), offsets);
                return ResponseEntity.ok().build();
        }

        /**
         * Get committed offsets.
         */
        @GetMapping("/groups/{groupId}/offsets")
        public ResponseEntity<CommittedOffsetsResponse> getCommittedOffsets(
                        @PathVariable String groupId) {

                Map<TopicPartition, OffsetAndMetadata> offsets = consumerService.getCommittedOffsets(groupId);

                List<OffsetData> offsetList = offsets.entrySet().stream()
                                .map(e -> new OffsetData(
                                                e.getKey().topic(),
                                                e.getKey().partition(),
                                                e.getValue().offset(),
                                                e.getValue().metadata()))
                                .toList();

                return ResponseEntity.ok(new CommittedOffsetsResponse(offsetList));
        }

        /**
         * Get consumer lag for a group.
         */
        @GetMapping("/groups/{groupId}/lag")
        public ResponseEntity<ConsumerLagResponse> getConsumerLag(@PathVariable String groupId) {
                Map<TopicPartition, Long> lag = consumerService.getConsumerLag(groupId);

                List<PartitionLag> lagList = lag.entrySet().stream()
                                .map(e -> new PartitionLag(e.getKey().topic(), e.getKey().partition(), e.getValue()))
                                .toList();

                long totalLag = lag.values().stream().mapToLong(Long::longValue).sum();

                return ResponseEntity.ok(new ConsumerLagResponse(totalLag, lagList));
        }

        // ==================== Seek Operations ====================

        /**
         * Seek to a specific offset.
         */
        @PostMapping("/groups/{groupId}/seek")
        public ResponseEntity<Void> seek(
                        @PathVariable String groupId,
                        @RequestBody SeekRequest request) throws IOException {

                consumerService.seek(
                                groupId,
                                new TopicPartition(request.topic(), request.partition()),
                                request.offset());
                return ResponseEntity.ok().build();
        }

        /**
         * Seek to beginning.
         */
        @PostMapping("/groups/{groupId}/seek-to-beginning")
        public ResponseEntity<Void> seekToBeginning(
                        @PathVariable String groupId,
                        @RequestBody SeekToEndpointRequest request) throws IOException {

                for (TopicPartitionData tp : request.partitions()) {
                        consumerService.seekToBeginning(groupId, new TopicPartition(tp.topic(), tp.partition()));
                }
                return ResponseEntity.ok().build();
        }

        /**
         * Seek to end.
         */
        @PostMapping("/groups/{groupId}/seek-to-end")
        public ResponseEntity<Void> seekToEnd(
                        @PathVariable String groupId,
                        @RequestBody SeekToEndpointRequest request) throws IOException {

                for (TopicPartitionData tp : request.partitions()) {
                        consumerService.seekToEnd(groupId, new TopicPartition(tp.topic(), tp.partition()));
                }
                return ResponseEntity.ok().build();
        }

        // ==================== Heartbeat ====================

        /**
         * Send heartbeat.
         */
        @PostMapping("/groups/{groupId}/heartbeat")
        public ResponseEntity<HeartbeatResponse> heartbeat(
                        @PathVariable String groupId,
                        @RequestBody HeartbeatRequest request) {

                HeartbeatResult result = consumerService.heartbeat(
                                groupId, request.memberId(), request.generation());

                return ResponseEntity.ok(new HeartbeatResponse(
                                result.errorCode().name(),
                                result.message()));
        }

        // ==================== Exception Handlers ====================

        @ExceptionHandler(UnknownMemberException.class)
        public ResponseEntity<ErrorResponse> handleUnknownMember(UnknownMemberException ex) {
                return ResponseEntity.badRequest()
                                .body(new ErrorResponse("UNKNOWN_MEMBER", ex.getMessage()));
        }

        @ExceptionHandler(OffsetOutOfRangeException.class)
        public ResponseEntity<ErrorResponse> handleOffsetOutOfRange(OffsetOutOfRangeException ex) {
                return ResponseEntity.badRequest()
                                .body(new ErrorResponse("OFFSET_OUT_OF_RANGE", ex.getMessage()));
        }

        // ==================== Request/Response DTOs ====================

        public record SubscribeRequest(
                        String memberId,
                        String clientId,
                        List<String> topics,
                        String assignmentStrategy // "range", "roundrobin", "sticky"
        ) {
        }

        public record SubscriptionResponse(
                        String groupId,
                        String memberId,
                        int generation,
                        String state) {
        }

        public record LeaveGroupRequest(String memberId) {
        }

        public record FetchRequest(
                        String memberId,
                        List<PartitionOffset> offsets,
                        Integer maxBytes,
                        Long maxWaitMs) {
        }

        public record PartitionOffset(String topic, int partition, long offset) {
        }

        public record FetchResponse(List<PartitionData> partitions) {
        }

        public record PartitionData(
                        String topic,
                        int partition,
                        String error,
                        long highWatermark,
                        long logEndOffset,
                        List<RecordData> records) {
        }

        public record RecordData(
                        long offset,
                        long timestamp,
                        String key,
                        String value,
                        Map<String, String> headers) {
        }

        public record CommitOffsetsRequest(
                        String memberId,
                        List<OffsetCommitData> offsets) {
        }

        public record OffsetCommitData(
                        String topic,
                        int partition,
                        long offset,
                        Integer leaderEpoch,
                        String metadata) {
        }

        public record CommittedOffsetsResponse(List<OffsetData> offsets) {
        }

        public record OffsetData(
                        String topic,
                        int partition,
                        long offset,
                        String metadata) {
        }

        public record ConsumerLagResponse(
                        long totalLag,
                        List<PartitionLag> partitions) {
        }

        public record PartitionLag(String topic, int partition, long lag) {
        }

        public record SeekRequest(String topic, int partition, long offset) {
        }

        public record SeekToEndpointRequest(List<TopicPartitionData> partitions) {
        }

        public record TopicPartitionData(String topic, int partition) {
        }

        public record HeartbeatRequest(String memberId, int generation) {
        }

        public record HeartbeatResponse(String errorCode, String message) {
        }

        public record ErrorResponse(String code, String message) {
        }
}
