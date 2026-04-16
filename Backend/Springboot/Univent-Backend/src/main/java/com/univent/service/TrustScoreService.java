package com.univent.service;

import com.univent.model.entity.User;
import com.univent.repository.ReviewRepository;
import com.univent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoreService {

    private final UserRepository userRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public void calculateAndPersist(User user) {
        int accountAgeScore = calculateAccountAgeScore(user.getCreatedAt());
        int reviewCountScore = calculateReviewCountScore(user.getTotalReviews());
        int helpfulnessScore = calculateHelpfulnessScore(user);
        int verifiedBonus = user.getVerifiedBadge() ? 20 : 0;

        int totalScore = accountAgeScore + reviewCountScore + helpfulnessScore + verifiedBonus;
        int trustScore = Math.min(100, totalScore);

        user.setTrustScore(trustScore);
        userRepository.save(user);

        log.info("Trust score calculated for user {}: {} (Age: {}, Reviews: {}, Help: {}, Verified: {})",
                user.getId(), trustScore, accountAgeScore, reviewCountScore, helpfulnessScore, verifiedBonus);
    }

    private int calculateAccountAgeScore(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        long monthsSinceCreation = ChronoUnit.MONTHS.between(createdAt, LocalDateTime.now());
        return (int) Math.min(20, monthsSinceCreation / 3);
    }

    private int calculateReviewCountScore(int totalReviews) {
        return Math.min(30, totalReviews * 3);
    }

    private int calculateHelpfulnessScore(User user) {
        // Find all published reviews for this user
        // We'll approximate this by finding the average net upvotes per review
        Double avgNetUpvotes = reviewRepository.findAverageNetUpvotesByUserId(user.getId());
        if (avgNetUpvotes == null) return 0;
        
        return (int) Math.min(30, avgNetUpvotes * 5);
    }
}
