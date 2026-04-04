package com.univent.model.dto.response;

import com.univent.model.enums.Role;
import com.univent.model.enums.VerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String anonymousUsername;
    private String avatarColor;
    private Role role;
    private VerificationStatus verificationStatus;
    private Boolean verifiedBadge;
    private Integer reputation;
    private Integer totalReviews;
    private Integer graduationYear;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
}