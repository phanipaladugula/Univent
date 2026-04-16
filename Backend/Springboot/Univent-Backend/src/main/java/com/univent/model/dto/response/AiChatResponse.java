package com.univent.model.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiChatResponse {
    private String response;
    private List<AiCitationResponse> citations;
    private AiChatMetadataResponse metadata;
}
