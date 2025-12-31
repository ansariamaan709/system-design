package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.util.List;

/**
 * Response for ListBuckets API (GET /).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "ListAllMyBucketsResult")
public class ListBucketsResponse {

    @JacksonXmlProperty(localName = "Owner")
    private OwnerDto owner;

    @JacksonXmlProperty(localName = "Bucket")
    @JacksonXmlElementWrapper(localName = "Buckets")
    private List<BucketDto> buckets;
}
