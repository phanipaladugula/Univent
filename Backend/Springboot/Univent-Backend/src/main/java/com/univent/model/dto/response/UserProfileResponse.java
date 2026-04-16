package com.univent.model.dto.response;

import com.univent.model.enums.VerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String anonymousUsername;
    private String avatarColor;
    private Integer reputation;
    private Integer totalReviews;
    private Integer graduationYear;
    private VerificationStatus verificationStatus;
    private Boolean verifiedBadge;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private long savedComparisonsCount;
    private List<SavedComparisonResponse> recentSavedComparisons;
}
