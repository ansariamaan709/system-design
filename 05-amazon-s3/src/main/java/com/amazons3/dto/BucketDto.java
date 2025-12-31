package com.amazons3.dto;

import com.amazons3.entity.Bucket;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * DTO for bucket information in S3 API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "Bucket")
public class BucketDto {

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "CreationDate")
    private String creationDate;

    public static BucketDto fromEntity(Bucket bucket) {
        return BucketDto.builder()
                .name(bucket.getBucketName())
                .creationDate(formatDate(bucket.getCreatedAt()))
                .build();
    }

    private static String formatDate(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
