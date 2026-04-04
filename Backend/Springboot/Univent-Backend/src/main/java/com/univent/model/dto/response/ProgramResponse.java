package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class ProgramResponse {
    private UUID id;
    private String name;
    private String slug;
    private String category;
    private String degree;
    private Integer durationYears;
    private String icon;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}