package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlagContentRequest {
    @NotBlank(message = "Reason is required")
    private String reason;
}