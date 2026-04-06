package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class StudentPostRequest {
    @NotNull(message = "College ID is required")
    private UUID collegeId;

    @NotBlank(message = "Content is required")
    @Size(max = 500, message = "Content must be less than 500 characters")
    private String content;
}