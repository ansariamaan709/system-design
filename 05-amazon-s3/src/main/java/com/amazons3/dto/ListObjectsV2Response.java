package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for ListObjectsV2 API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "ListBucketResult")
public class ListObjectsV2Response {

    @JacksonXmlProperty(localName = "IsTruncated")
    private Boolean isTruncated;

    @JacksonXmlProperty(localName = "Contents")
    @JacksonXmlElementWrapper(useWrapping = false)
    @Builder.Default
    private List<ObjectDto> contents = new ArrayList<>();

    @JacksonXmlProperty(localName = "Name")
    private String name;

    @JacksonXmlProperty(localName = "Prefix")
    private String prefix;

    @JacksonXmlProperty(localName = "Delimiter")
    private String delimiter;

    @JacksonXmlProperty(localName = "MaxKeys")
    private Integer maxKeys;

    @JacksonXmlProperty(localName = "CommonPrefixes")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<CommonPrefix> commonPrefixes;

    @JacksonXmlProperty(localName = "EncodingType")
    private String encodingType;

    @JacksonXmlProperty(localName = "KeyCount")
    private Integer keyCount;

    @JacksonXmlProperty(localName = "ContinuationToken")
    private String continuationToken;

    @JacksonXmlProperty(localName = "NextContinuationToken")
    private String nextContinuationToken;

    @JacksonXmlProperty(localName = "StartAfter")
    private String startAfter;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommonPrefix {
        @JacksonXmlProperty(localName = "Prefix")
        private String prefix;
    }
}
