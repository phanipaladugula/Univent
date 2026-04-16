package com.univent.controller;

import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.entity.User;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.UserRepository;
import com.univent.repository.ReviewRepository;
import com.univent.service.ReviewService;
import com.univent.kafka.ReviewEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ReviewRepository reviewRepository;
    private final ReviewEventProducer reviewEventProducer;

    @GetMapping("/pending")
    public ResponseEntity<Page<ReviewResponse>> getPendingReviews(Pageable pageable) {
        log.info("Fetching pending reviews for admin");
        return ResponseEntity.ok(reviewService.getReviewsByStatus(ReviewStatus.PENDING, pageable));
    }

    @GetMapping("/published")
    public ResponseEntity<Page<ReviewResponse>> getPublishedReviews(Pageable pageable) {
        log.info("Fetching published reviews for admin");
        return ResponseEntity.ok(reviewService.getReviewsByStatus(ReviewStatus.PUBLISHED, pageable));
    }

    @GetMapping("/flagged")
    public ResponseEntity<Page<ReviewResponse>> getFlaggedReviews(Pageable pageable) {
        log.info("Fetching flagged reviews for admin");
        return ResponseEntity.ok(reviewService.getReviewsByStatus(ReviewStatus.FLAGGED, pageable));
    }

    @PutMapping("/{reviewId}/approve")
    public ResponseEntity<ReviewResponse> approveReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        log.info("Admin {} approving review: {}", admin.getAnonymousUsername(), reviewId);
        ReviewResponse response = reviewService.approveReview(reviewId, admin);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{reviewId}/reject")
    public ResponseEntity<Void> rejectReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId,
            @RequestParam String reason) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        log.info("Admin {} rejecting review: {} with reason: {}", admin.getAnonymousUsername(), reviewId, reason);
        reviewService.rejectReview(reviewId, admin, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reviewId}/reprocess")
    public ResponseEntity<String> reprocessReview(@PathVariable UUID reviewId) {
        log.info("Admin requesting reprocess for review: {}", reviewId);
        com.univent.model.entity.Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        
        reviewEventProducer.publishReviewSubmitted(
                review.getId(), review.getCollege().getId(), review.getProgram().getId(),
                review.getReviewText(), review.getPros(), review.getCons(),
                review.getUser().getId(), review.getUser().getVerifiedBadge(),
                review.getGraduationYear(), review.getOverallRating());
                
        return ResponseEntity.ok("Review " + reviewId + " re-published for processing.");
    }
}