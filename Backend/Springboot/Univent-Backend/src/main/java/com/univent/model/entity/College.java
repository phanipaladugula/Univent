package com.univent.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "colleges")
@Getter
@Setter
public class College extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    private String city;

    private String state;

    @Column(name = "college_type")
    private String collegeType;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    private String website;

    @Column(name = "email_domain")
    private String emailDomain;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;
}