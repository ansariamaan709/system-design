package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * MultipartPart entity - represents individual parts of a multipart upload.
 */
@Entity
@Table(name = "multipart_parts", indexes = {
        @Index(name = "idx_multipart_parts_upload", columnList = "upload_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "upload_id", "part_number" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "part_id")
    private Long partId;

    @Column(name = "upload_id", nullable = false, length = 64)
    private String uploadId;

    @Column(name = "part_number", nullable = false)
    private Integer partNumber;

    @Column(name = "etag", nullable = false, length = 64)
    private String etag;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "uploaded_at")
    @Builder.Default
    private Instant uploadedAt = Instant.now();
}
