package com.univent.model.entity;

import com.univent.model.enums.Role;
import com.univent.model.enums.VerificationStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
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

    @Column(name = "trust_score", nullable = false)
    private Integer trustScore = 0;

    @Column(name = "total_reviews")
    private Integer totalReviews = 0;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    // Verification fields
    @Column(name = "id_card_path")
    private String idCardPath;

    @Column(name = "id_card_uploaded_at")
    private LocalDateTime idCardUploadedAt;

    @Column(name = "verification_requested_at")
    private LocalDateTime verificationRequestedAt;

    @Column(name = "verification_reviewed_by")
    private UUID verificationReviewedBy;

    @Column(name = "verification_reviewed_at")
    private LocalDateTime verificationReviewedAt;

    @Column(name = "verification_rejection_reason")
    private String verificationRejectionReason;

    // Getters and Setters
    public String getEmailHash() { return emailHash; }
    public void setEmailHash(String emailHash) { this.emailHash = emailHash; }

    public String getAnonymousUsername() { return anonymousUsername; }
    public void setAnonymousUsername(String anonymousUsername) { this.anonymousUsername = anonymousUsername; }

    public String getAvatarColor() { return avatarColor; }
    public void setAvatarColor(String avatarColor) { this.avatarColor = avatarColor; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus verificationStatus) { this.verificationStatus = verificationStatus; }

    public Boolean getVerifiedBadge() { return verifiedBadge; }
    public void setVerifiedBadge(Boolean verifiedBadge) { this.verifiedBadge = verifiedBadge; }

    public Integer getReputation() { return reputation; }
    public void setReputation(Integer reputation) { this.reputation = reputation; }

    public Integer getTrustScore() { return trustScore; }
    public void setTrustScore(Integer trustScore) { this.trustScore = trustScore; }

    public Integer getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Integer totalReviews) { this.totalReviews = totalReviews; }

    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer graduationYear) { this.graduationYear = graduationYear; }

    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public String getIdCardPath() { return idCardPath; }
    public void setIdCardPath(String idCardPath) { this.idCardPath = idCardPath; }

    public LocalDateTime getIdCardUploadedAt() { return idCardUploadedAt; }
    public void setIdCardUploadedAt(LocalDateTime idCardUploadedAt) { this.idCardUploadedAt = idCardUploadedAt; }

    public LocalDateTime getVerificationRequestedAt() { return verificationRequestedAt; }
    public void setVerificationRequestedAt(LocalDateTime verificationRequestedAt) { this.verificationRequestedAt = verificationRequestedAt; }

    public UUID getVerificationReviewedBy() { return verificationReviewedBy; }
    public void setVerificationReviewedBy(UUID verificationReviewedBy) { this.verificationReviewedBy = verificationReviewedBy; }

    public LocalDateTime getVerificationReviewedAt() { return verificationReviewedAt; }
    public void setVerificationReviewedAt(LocalDateTime verificationReviewedAt) { this.verificationReviewedAt = verificationReviewedAt; }

    public String getVerificationRejectionReason() { return verificationRejectionReason; }
    public void setVerificationRejectionReason(String verificationRejectionReason) { this.verificationRejectionReason = verificationRejectionReason; }
}