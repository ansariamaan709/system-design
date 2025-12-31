package com.kafka.controller;

import com.kafka.service.ProducerService;
import com.kafka.storage.Record;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Producer Controller - REST API for producing messages to Kafka.
 * 
 * Endpoints:
 * - Send single message
 * - Send batch of messages
 * - Transactional produce
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/producer")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;

    /**
     * Produce a single message to a topic.
     */
    @PostMapping("/topics/{topic}")
    public ResponseEntity<ProduceResponse> produce(
            @PathVariable String topic,
            @RequestBody ProduceRequest request) throws ExecutionException, InterruptedException, TimeoutException {

        log.debug("Producing message to topic {}", topic);

        byte[] key = request.key() != null
                ? decodeValue(request.key(), request.keyEncoding())
                : null;
        byte[] value = decodeValue(request.value(), request.valueEncoding());

        List<Record.Header> headers = null;
        if (request.headers() != null) {
            headers = request.headers().stream()
                    .map(h -> new Record.Header(h.key(), h.value().getBytes(StandardCharsets.UTF_8)))
                    .toList();
        }

        ProducerService.ProducerRecord record = new ProducerService.ProducerRecord(
                topic,
                request.partition(),
                request.timestamp(),
                key,
                value,
                headers);

        CompletableFuture<ProducerService.ProduceResult> future = producerService.send(record);
        ProducerService.ProduceResult result = future.get(30, TimeUnit.SECONDS);

        return ResponseEntity.ok(new ProduceResponse(
                result.topic(),
                result.partition(),
                result.offset(),
                result.timestamp()));
    }

    /**
     * Produce a batch of messages to a topic.
     */
    @PostMapping("/topics/{topic}/batch")
    public ResponseEntity<BatchProduceResponse> produceBatch(
            @PathVariable String topic,
            @RequestBody BatchProduceRequest request)
            throws ExecutionException, InterruptedException, TimeoutException {

        log.debug("Producing {} messages to topic {}", request.records().size(), topic);

        List<ProducerService.ProducerRecord> records = new ArrayList<>();
        for (RecordData recordData : request.records()) {
            byte[] key = recordData.key() != null
                    ? decodeValue(recordData.key(), request.keyEncoding())
                    : null;
            byte[] value = decodeValue(recordData.value(), request.valueEncoding());

            List<Record.Header> headers = null;
            if (recordData.headers() != null) {
                headers = recordData.headers().stream()
                        .map(h -> new Record.Header(h.key(), h.value().getBytes(StandardCharsets.UTF_8)))
                        .toList();
            }

            records.add(new ProducerService.ProducerRecord(
                    topic,
                    recordData.partition(),
                    recordData.timestamp(),
                    key,
                    value,
                    headers));
        }

        CompletableFuture<List<ProducerService.ProduceResult>> future = producerService.sendBatch(records);
        List<ProducerService.ProduceResult> results = future.get(60, TimeUnit.SECONDS);

        List<ProduceResponse> responses = results.stream()
                .map(r -> new ProduceResponse(r.topic(), r.partition(), r.offset(), r.timestamp()))
                .toList();

        return ResponseEntity.ok(new BatchProduceResponse(responses));
    }

    /**
     * Begin a transaction.
     */
    @PostMapping("/transactions/{transactionalId}/begin")
    public ResponseEntity<Void> beginTransaction(@PathVariable String transactionalId) {
        producerService.beginTransaction(transactionalId);
        return ResponseEntity.ok().build();
    }

    /**
     * Commit a transaction.
     */
    @PostMapping("/transactions/{transactionalId}/commit")
    public ResponseEntity<Void> commitTransaction(@PathVariable String transactionalId) {
        producerService.commitTransaction(transactionalId);
        return ResponseEntity.ok().build();
    }

    /**
     * Abort a transaction.
     */
    @PostMapping("/transactions/{transactionalId}/abort")
    public ResponseEntity<Void> abortTransaction(@PathVariable String transactionalId) {
        producerService.abortTransaction(transactionalId);
        return ResponseEntity.ok().build();
    }

    /**
     * Flush all pending messages.
     */
    @PostMapping("/flush")
    public ResponseEntity<Void> flush() {
        producerService.flush();
        return ResponseEntity.ok().build();
    }

    private byte[] decodeValue(String value, String encoding) {
        if (value == null)
            return null;

        return switch (encoding != null ? encoding.toLowerCase() : "string") {
            case "base64" -> Base64.getDecoder().decode(value);
            case "hex" -> hexToBytes(value);
            default -> value.getBytes(StandardCharsets.UTF_8);
        };
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    // ==================== Request/Response DTOs ====================

    public record ProduceRequest(
            Integer partition,
            Long timestamp,
            String key,
            String value,
            String keyEncoding, // "string" (default), "base64", "hex"
            String valueEncoding, // "string" (default), "base64", "hex"
            List<HeaderData> headers) {
    }

    public record BatchProduceRequest(
            List<RecordData> records,
            String keyEncoding,
            String valueEncoding) {
    }

    public record RecordData(
            Integer partition,
            Long timestamp,
            String key,
            String value,
            List<HeaderData> headers) {
    }

    public record HeaderData(String key, String value) {
    }

    public record ProduceResponse(
            String topic,
            int partition,
            long offset,
            long timestamp) {
    }

    public record BatchProduceResponse(List<ProduceResponse> results) {
    }
}
