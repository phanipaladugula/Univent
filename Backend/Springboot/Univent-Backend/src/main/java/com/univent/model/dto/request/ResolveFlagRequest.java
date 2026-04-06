package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResolveFlagRequest {
    @NotNull(message = "Action is required")
    private Action action;

    private String reason;

    public enum Action {
        REMOVE_CONTENT,
        DISMISS_FLAG
    }
}