package com.univent.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "college_programs")
@Getter
@Setter
public class CollegeProgram extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "college_id", nullable = false)
    private College college;

    @ManyToOne
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "fees_total")
    private BigDecimal feesTotal;

    @Column(name = "fees_per_year")
    private BigDecimal feesPerYear;

    @Column(name = "seats_intake")
    private Integer seatsIntake;

    @Column(name = "entrance_exam")
    private String entranceExam;

    @Column(name = "cutoff_rank")
    private String cutoffRank;

    @Column(name = "median_package")
    private BigDecimal medianPackage;

    @Column(name = "highest_package")
    private BigDecimal highestPackage;

    @Column(name = "placement_percentage")
    private BigDecimal placementPercentage;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;
}