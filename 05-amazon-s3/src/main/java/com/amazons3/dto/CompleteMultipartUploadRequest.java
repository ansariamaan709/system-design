package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.util.List;

/**
 * Request body for CompleteMultipartUpload API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "CompleteMultipartUpload")
public class CompleteMultipartUploadRequest {

    @JacksonXmlProperty(localName = "Part")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<PartInfo> parts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartInfo {
        @JacksonXmlProperty(localName = "PartNumber")
        private Integer partNumber;

        @JacksonXmlProperty(localName = "ETag")
        private String etag;
    }
}
