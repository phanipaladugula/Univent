package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SavedComparisonResponse {
    private UUID id;
    private String name;
    private List<UUID> collegeIds;
    private UUID programId;
    private LocalDateTime createdAt;
}