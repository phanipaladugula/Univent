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

@Data
@Builder
public class CollegeComparisonData {
    private CollegeResponse college;
    private ProgramResponse program;
    private BigDecimal overallRating;
    private Integer reviewCount;
    private BigDecimal placementPercentage;
    private BigDecimal medianPackage;
    private BigDecimal totalFees;
    private BigDecimal teachingQuality;
    private BigDecimal infrastructure;
    private BigDecimal hostelLife;
    private BigDecimal campusLife;
    private BigDecimal valueForMoney;
    private Integer wouldRecommendPercent;
    private BigDecimal roiScore;
    private Integer rank;
}

@Data
@Builder
public class ROIComparison {
    private List<ROIData> rois;
    private String bestValueCollege;
    private String bestRoiCollege;
    private String bestPackageCollege;
}

@Data
@Builder
public class ROIData {
    private String collegeName;
    private BigDecimal totalFees;
    private BigDecimal medianPackage;
    private Integer durationYears;
    private BigDecimal roiPerYear;
    private BigDecimal roiPercentage;
    private String roiLabel;
}

@Data
@Builder
public class SavedComparison {
    private UUID id;
    private String name;
    private List<UUID> collegeIds;
    private UUID programId;
    private LocalDateTime createdAt;
}