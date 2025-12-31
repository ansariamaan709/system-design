package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

/**
 * Response for CompleteMultipartUpload API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "CompleteMultipartUploadResult")
public class CompleteMultipartUploadResponse {

    @JacksonXmlProperty(localName = "Location")
    private String location;

    @JacksonXmlProperty(localName = "Bucket")
    private String bucket;

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "ETag")
    private String etag;

    @JacksonXmlProperty(localName = "ChecksumCRC32")
    private String checksumCrc32;

    @JacksonXmlProperty(localName = "ChecksumCRC32C")
    private String checksumCrc32c;

    @JacksonXmlProperty(localName = "ChecksumSHA1")
    private String checksumSha1;

    @JacksonXmlProperty(localName = "ChecksumSHA256")
    private String checksumSha256;
}
