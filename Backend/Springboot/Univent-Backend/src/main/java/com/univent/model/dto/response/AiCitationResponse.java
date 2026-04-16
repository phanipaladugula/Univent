package com.univent.model.dto.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.UUID;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AiCitationResponse {
    private UUID reviewId;
    private String excerpt;
    private Integer rating;
    private Boolean verified;
    private Integer year;
}
