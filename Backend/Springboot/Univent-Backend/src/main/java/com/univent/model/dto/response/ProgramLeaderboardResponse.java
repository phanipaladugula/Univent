package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class ProgramLeaderboardResponse {
    private Integer rankInCategory;
    private Integer overallRank;
    private UUID collegeId;
    private String collegeName;
    private String collegeSlug;
    private String city;
    private String state;
    private UUID programId;
    private String programName;
    private String category;
    private BigDecimal overallRating;
    private Integer reviewCount;
    private BigDecimal placementPercentage;
    private BigDecimal medianPackage;
    private BigDecimal totalFees;
    private BigDecimal weightedScore;
}