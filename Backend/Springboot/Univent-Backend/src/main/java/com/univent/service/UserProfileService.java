package com.univent.service;

import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.dto.response.SavedComparisonResponse;
import com.univent.model.dto.response.UserProfileResponse;
import com.univent.model.entity.User;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final ComparisonService comparisonService;

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(String emailHash) {
        User user = getUserByEmailHash(emailHash);
        Page<SavedComparisonResponse> savedComparisons = comparisonService.getUserSavedComparisons(
                user, PageRequest.of(0, 5));

        return UserProfileResponse.builder()
                .id(user.getId())
                .anonymousUsername(user.getAnonymousUsername())
                .avatarColor(user.getAvatarColor())
                .reputation(user.getReputation())
                .totalReviews(user.getTotalReviews())
                .graduationYear(user.getGraduationYear())
                .verificationStatus(user.getVerificationStatus())
                .verifiedBadge(user.getVerifiedBadge())
                .createdAt(user.getCreatedAt())
                .lastActiveAt(user.getLastActiveAt())
                .savedComparisonsCount(savedComparisons.getTotalElements())
                .recentSavedComparisons(savedComparisons.getContent())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getCurrentUserReviews(String emailHash, String status, Pageable pageable) {
        User user = getUserByEmailHash(emailHash);
        return reviewService.getReviewsByUser(user.getId(), parseStatus(status), pageable);
    }

    @Transactional(readOnly = true)
    public Page<SavedComparisonResponse> getCurrentUserSavedComparisons(String emailHash, Pageable pageable) {
        User user = getUserByEmailHash(emailHash);
        return comparisonService.getUserSavedComparisons(user, pageable);
    }

    private User getUserByEmailHash(String emailHash) {
        return userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private ReviewStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return ReviewStatus.PUBLISHED;
        }

        try {
            return ReviewStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Unsupported review status: " + status);
        }
    }
}
