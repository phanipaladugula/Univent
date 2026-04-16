package com.univent.model.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiSummarizeResponse {
    private String summary;
    private List<String> strengths;
    private List<String> weaknesses;
    private Integer reviewsAnalyzed;
}
