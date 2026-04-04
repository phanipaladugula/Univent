package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProgramRequest {
    @NotBlank(message = "Program name is required")
    private String name;

    @NotBlank(message = "Category is required")
    private String category;

    private String degree;
    private Integer durationYears;
    private String icon;
}