package com.univent.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class ReviewSubmitRequest {

    @NotNull(message = "College ID is required")
    private UUID collegeId;

    @NotNull(message = "Program ID is required")
    private UUID programId;

    @NotNull(message = "Graduation year is required")
    @Min(value = 1950, message = "Invalid graduation year")
    @Max(value = 2030, message = "Invalid graduation year")
    private Integer graduationYear;

    private Boolean isCurrentStudent = false;

    @NotNull(message = "Overall rating is required")
    @Min(1) @Max(5)
    private Integer overallRating;

    @NotNull(message = "Teaching quality rating is required")
    @Min(1) @Max(5)
    private Integer teachingQuality;

    @NotNull(message = "Placement support rating is required")
    @Min(1) @Max(5)
    private Integer placementSupport;

    @NotNull(message = "Infrastructure rating is required")
    @Min(1) @Max(5)
    private Integer infrastructure;

    @NotNull(message = "Hostel life rating is required")
    @Min(1) @Max(5)
    private Integer hostelLife;

    @NotNull(message = "Campus life rating is required")
    @Min(1) @Max(5)
    private Integer campusLife;

    @NotNull(message = "Value for money rating is required")
    @Min(1) @Max(5)
    private Integer valueForMoney;

    @NotEmpty(message = "At least one pro is required")
    private List<String> pros;

    @NotEmpty(message = "At least one con is required")
    private List<String> cons;

    @NotBlank(message = "Review text is required")
    @Size(min = 100, message = "Review must be at least 100 characters")
    private String reviewText;

    @NotNull(message = "Would recommend is required")
    private Boolean wouldRecommend;

    private BigDecimal cgpa;
    private BigDecimal placementPackage;
}