package com.univent.model.entity;

import com.univent.model.enums.ReviewStatus;
import com.univent.model.enums.SentimentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;  // ADD THIS
import java.util.List;      // ADD THIS

@Entity
@Table(name = "reviews")
@Getter
@Setter
public class Review extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "college_id", nullable = false)
    private College college;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "graduation_year", nullable = false)
    private Integer graduationYear;

    @Column(name = "is_current_student")
    private Boolean isCurrentStudent = false;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(name = "teaching_quality", nullable = false)
    private Integer teachingQuality;

    @Column(name = "placement_support", nullable = false)
    private Integer placementSupport;

    @Column(nullable = false)
    private Integer infrastructure;

    @Column(name = "hostel_life", nullable = false)
    private Integer hostelLife;

    @Column(name = "campus_life", nullable = false)
    private Integer campusLife;

    @Column(name = "value_for_money", nullable = false)
    private Integer valueForMoney;

    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] pros;

    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] cons;

    @Column(name = "review_text", nullable = false, columnDefinition = "TEXT")
    private String reviewText;

    @Column(name = "would_recommend", nullable = false)
    private Boolean wouldRecommend;

    private BigDecimal cgpa;

    @Column(name = "placement_package")
    private BigDecimal placementPackage;

    private Integer upvotes = 0;

    private Integer downvotes = 0;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "is_verified_review")
    private Boolean isVerifiedReview = false;

    @Enumerated(EnumType.STRING)
    private SentimentType sentiment;

    @Column(name = "sentiment_score")
    private BigDecimal sentimentScore;

    @Column(columnDefinition = "TEXT[]")
    private String[] extractedTopics;

    @Column(name = "is_ai_processed")
    private Boolean isAiProcessed = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL)
    private List<ReviewVote> votes = new ArrayList<>();
}