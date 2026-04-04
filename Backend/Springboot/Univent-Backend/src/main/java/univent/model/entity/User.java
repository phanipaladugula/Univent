package com.univent.model.entity;

import com.univent.model.enums.Role;
import com.univent.model.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(name = "email_hash", unique = true, nullable = false)
    private String emailHash;

    @Column(name = "anonymous_username", unique = true, nullable = false)
    private String anonymousUsername;

    @Column(name = "avatar_color", nullable = false)
    private String avatarColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "verified_badge")
    private Boolean verifiedBadge = false;

    @Column
    private Integer reputation = 0;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
}