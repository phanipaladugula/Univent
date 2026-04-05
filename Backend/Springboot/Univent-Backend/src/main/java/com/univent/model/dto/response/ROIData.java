package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

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