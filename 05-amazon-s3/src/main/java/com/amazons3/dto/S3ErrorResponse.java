package com.amazons3.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

/**
 * S3 Error response format.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JacksonXmlRootElement(localName = "Error")
public class S3ErrorResponse {

    @JacksonXmlProperty(localName = "Code")
    private String code;

    @JacksonXmlProperty(localName = "Message")
    private String message;

    @JacksonXmlProperty(localName = "Resource")
    private String resource;

    @JacksonXmlProperty(localName = "RequestId")
    private String requestId;

    public static S3ErrorResponse of(String code, String message) {
        return S3ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }

    public static S3ErrorResponse of(String code, String message, String resource) {
        return S3ErrorResponse.builder()
                .code(code)
                .message(message)
                .resource(resource)
                .build();
    }
}
