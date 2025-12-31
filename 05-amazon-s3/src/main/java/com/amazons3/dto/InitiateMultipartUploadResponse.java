package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

/**
 * Response for InitiateMultipartUpload API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "InitiateMultipartUploadResult")
public class InitiateMultipartUploadResponse {

    @JacksonXmlProperty(localName = "Bucket")
    private String bucket;

    @JacksonXmlProperty(localName = "Key")
    private String key;

    @JacksonXmlProperty(localName = "UploadId")
    private String uploadId;
}
