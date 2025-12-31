package com.amazons3.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * ObjectMetadata entity - stores custom user metadata for objects.
 */
@Entity
@Table(name = "object_metadata", indexes = {
        @Index(name = "idx_object_metadata_object", columnList = "object_id")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "object_id", "meta_key" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ObjectMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "metadata_id")
    private Long metadataId;

    @Column(name = "object_id", nullable = false)
    private Long objectId;

    @Column(name = "meta_key", nullable = false)
    private String metaKey;

    @Column(name = "meta_value", columnDefinition = "TEXT")
    private String metaValue;
}
