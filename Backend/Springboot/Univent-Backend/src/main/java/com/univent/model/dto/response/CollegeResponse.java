package com.univent.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CollegeResponse {
    private UUID id;
    private String name;
    private String slug;
    private String city;
    private String state;
    private String collegeType;
    private Boolean isVerified;
    private String website;
    private String emailDomain;
    private String logoUrl;
    private BigDecimal averageRating;
    private Integer totalReviews;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}