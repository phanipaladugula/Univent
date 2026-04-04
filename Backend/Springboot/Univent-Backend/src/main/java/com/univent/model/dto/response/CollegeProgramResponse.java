package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CollegeProgramResponse {
    private UUID id;
    private CollegeResponse college;
    private ProgramResponse program;
    private BigDecimal feesTotal;
    private BigDecimal feesPerYear;
    private Integer seatsIntake;
    private String entranceExam;
    private String cutoffRank;
    private BigDecimal medianPackage;
    private BigDecimal highestPackage;
    private BigDecimal placementPercentage;
    private BigDecimal averageRating;
    private Integer totalReviews;
}