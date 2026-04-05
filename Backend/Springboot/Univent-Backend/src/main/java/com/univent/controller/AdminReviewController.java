package com.univent.controller;

import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.entity.User;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.UserRepository;
import com.univent.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<Page<ReviewResponse>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Admin fetching pending reviews");
        return ResponseEntity.ok(reviewService.getReviewsByStatus(ReviewStatus.PENDING, pageable));
    }

    @PutMapping("/{reviewId}/approve")
    public ResponseEntity<Void> approveReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        log.info("Admin {} approving review: {}", admin.getAnonymousUsername(), reviewId);
        reviewService.updateReviewStatus(reviewId, ReviewStatus.PUBLISHED, admin, null);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{reviewId}/reject")
    public ResponseEntity<Void> rejectReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId,
            @RequestParam String reason) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        log.info("Admin {} rejecting review: {} with reason: {}", admin.getAnonymousUsername(), reviewId, reason);
        reviewService.updateReviewStatus(reviewId, ReviewStatus.REMOVED, admin, reason);
        return ResponseEntity.ok().build();
    }
}