package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

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