package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FlaggedContentResponse {
    private UUID id;
    private String contentType; // REVIEW or COMMENT
    private UUID contentId;
    private String contentSnippet;
    private String flaggedByUsername;
    private UUID flaggedById;
    private String reason;
    private String status;
    private String resolvedByUsername;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    // For reviews
    private CollegeResponse college;
    private ProgramResponse program;
    private Integer overallRating;

    // For comments
    private String commentText;
}