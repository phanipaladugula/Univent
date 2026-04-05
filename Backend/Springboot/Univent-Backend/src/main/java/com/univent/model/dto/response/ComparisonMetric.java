package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ComparisonMetric {
    private String metricName;
    private String metricKey;
    private String unit;
    private BigDecimal college1Value;
    private BigDecimal college2Value;
    private BigDecimal college3Value;
    private BigDecimal college4Value;
    private String winner;
    private String tooltip;
}