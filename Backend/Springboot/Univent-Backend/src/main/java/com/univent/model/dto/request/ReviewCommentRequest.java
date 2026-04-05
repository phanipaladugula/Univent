package com.univent.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ReviewCommentRequest {
    @NotBlank(message = "Comment text is required")
    @Size(min = 1, max = 500, message = "Comment must be between 1 and 500 characters")
    private String commentText;

    private UUID parentCommentId;
}