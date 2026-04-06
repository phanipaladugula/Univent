package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NewsItemResponse {
    private UUID id;
    private String type; // "ARTICLE" or "POST"
    private String title;
    private String content;
    private String source;
    private String url;
    private String author;
    private String authorAvatarColor;
    private UUID collegeId;
    private String collegeName;
    private Integer upvotes;
    private LocalDateTime publishedAt;
    private String status;
}