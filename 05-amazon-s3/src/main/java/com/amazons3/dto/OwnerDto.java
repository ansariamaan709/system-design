package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.*;

/**
 * DTO for owner information in S3 API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerDto {

    @JacksonXmlProperty(localName = "ID")
    private String id;

    @JacksonXmlProperty(localName = "DisplayName")
    private String displayName;
}
