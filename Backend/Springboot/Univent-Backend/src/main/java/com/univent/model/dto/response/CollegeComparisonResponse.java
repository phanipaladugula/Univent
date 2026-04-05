package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CollegeComparisonResponse {
    private List<UUID> collegeIds;
    private UUID programId;
    private List<CollegeComparisonData> colleges;
    private List<ComparisonMetric> metrics;
    private ROIComparison roiComparison;
    private String recommendation;
    private Map<String, BigDecimal> weightedScores;
    private LocalDateTime generatedAt;
}