package com.amazons3.dto;

import com.amazons3.entity.S3Object;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * DTO for object information in S3 API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "Contents")
public class ObjectDto {

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "LastModified")
    private String lastModified;

    @JacksonXmlProperty(localName = "ETag")
    private String etag;

    @JacksonXmlProperty(localName = "Size")
    private Long size;

    @JacksonXmlProperty(localName = "StorageClass")
    private String storageClass;

    @JacksonXmlProperty(localName = "Owner")
    private OwnerDto owner;

    public static ObjectDto fromEntity(S3Object object) {
        return ObjectDto.builder()
                .key(object.getObjectKey())
                .lastModified(formatDate(object.getLastModified()))
                .etag(object.getEtag())
                .size(object.getSizeBytes())
                .storageClass(object.getStorageClass().name())
                .build();
    }

    public static ObjectDto fromEntity(S3Object object, OwnerDto owner) {
        ObjectDto dto = fromEntity(object);
        dto.setOwner(owner);
        return dto;
    }

    private static String formatDate(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }
}
