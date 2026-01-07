package com.uber.dto;

import com.uber.entity.DriverStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Request DTO for updating driver status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverStatusRequest {

    @NotNull(message = "Status is required")
    private DriverStatus status;
}
