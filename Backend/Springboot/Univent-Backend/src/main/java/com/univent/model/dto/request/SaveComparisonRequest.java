package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SaveComparisonRequest {
    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 100, message = "Name must be between 3 and 100 characters")
    private String name;

    @NotNull(message = "College IDs are required")
    @Size(min = 2, max = 4, message = "Compare between 2 to 4 colleges")
    private List<UUID> collegeIds;

    @NotNull(message = "Program ID is required")
    private UUID programId;
}