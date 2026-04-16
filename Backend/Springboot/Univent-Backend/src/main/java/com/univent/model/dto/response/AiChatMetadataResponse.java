package com.univent.model.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiChatMetadataResponse {
    private Integer reviewsUsed;
    private Integer verifiedCount;
    private String confidence;
    private List<String> toolsCalled;
    private Integer processingTimeMs;
}
