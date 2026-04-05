package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReviewCommentResponse {
    private UUID id;
    private UserResponse user;
    private String commentText;
    private Integer upvotes;
    private List<ReviewCommentResponse> replies;
    private LocalDateTime createdAt;
}