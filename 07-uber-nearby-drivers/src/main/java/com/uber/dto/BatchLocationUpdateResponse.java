package com.uber.dto;

import lombok.*;

/**
 * Response DTO for batch location updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchLocationUpdateResponse {

    private int processed;
    private int failed;
    private String message;

    public static BatchLocationUpdateResponse success(int processed) {
        return BatchLocationUpdateResponse.builder()
                .processed(processed)
                .failed(0)
                .message("All locations processed successfully")
                .build();
    }

    public static BatchLocationUpdateResponse partial(int processed, int failed) {
        return BatchLocationUpdateResponse.builder()
                .processed(processed)
                .failed(failed)
                .message(String.format("%d locations processed, %d failed", processed, failed))
                .build();
    }
}
