package com.univent.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CollegeProgramRequest {
    @NotNull(message = "College ID is required")
    private UUID collegeId;

    @NotNull(message = "Program ID is required")
    private UUID programId;

    private BigDecimal feesTotal;
    private BigDecimal feesPerYear;
    private Integer seatsIntake;
    private String entranceExam;
    private String cutoffRank;
    private BigDecimal medianPackage;
    private BigDecimal highestPackage;
    private BigDecimal placementPercentage;
}