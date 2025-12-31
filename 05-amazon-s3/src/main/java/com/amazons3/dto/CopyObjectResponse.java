package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Response for CopyObject API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "CopyObjectResult")
public class CopyObjectResponse {

    @JacksonXmlProperty(localName = "ETag")
    private String etag;

    @JacksonXmlProperty(localName = "LastModified")
    private String lastModified;

    @JacksonXmlProperty(localName = "ChecksumCRC32")
    private String checksumCrc32;

    @JacksonXmlProperty(localName = "ChecksumSHA256")
    private String checksumSha256;

    public static CopyObjectResponse create(String etag, Instant lastModified) {
        return CopyObjectResponse.builder()
                .etag(etag)
                .lastModified(DateTimeFormatter.ISO_INSTANT.format(lastModified))
                .build();
    }
}
