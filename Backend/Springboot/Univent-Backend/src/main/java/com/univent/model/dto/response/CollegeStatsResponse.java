package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CollegeStatsResponse {
    private UUID collegeId;
    private String collegeName;
    private String slug;
    private String city;
    private String state;
    private String collegeType;
    private Integer totalReviews;
    private BigDecimal avgOverallRating;
    private BigDecimal avgTeachingQuality;
    private BigDecimal avgInfrastructure;
    private BigDecimal avgHostelLife;
    private BigDecimal avgCampusLife;
    private BigDecimal avgValueForMoney;
    private Integer totalPrograms;
    private BigDecimal avgMedianPackage;
    private BigDecimal avgPlacementPercentage;
    private LocalDateTime calculatedAt;
}