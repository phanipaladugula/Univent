package com.univent.model.dto.response;

import com.univent.model.enums.ReviewStatus;
import com.univent.model.enums.SentimentType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReviewResponse {
    private UUID id;
    private UserResponse user;
    private CollegeResponse college;
    private ProgramResponse program;
    private Integer graduationYear;
    private Boolean isCurrentStudent;
    private Integer overallRating;
    private Integer teachingQuality;
    private Integer placementSupport;
    private Integer infrastructure;
    private Integer hostelLife;
    private Integer campusLife;
    private Integer valueForMoney;
    private List<String> pros;
    private List<String> cons;
    private String reviewText;
    private Boolean wouldRecommend;
    private BigDecimal cgpa;
    private BigDecimal placementPackage;
    private Integer upvotes;
    private Integer downvotes;
    private ReviewStatus status;
    private Boolean isVerifiedReview;
    private SentimentType sentiment;
    private BigDecimal sentimentScore;
    private List<String> extractedTopics;
    private Integer helpfulScore; // upvotes - downvotes
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}