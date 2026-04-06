package com.univent.controller;

import com.univent.model.dto.request.FlagContentRequest;
import com.univent.model.dto.request.ReviewCommentRequest;
import com.univent.model.dto.request.ReviewSubmitRequest;
import com.univent.model.dto.request.ReviewVoteRequest;
import com.univent.model.dto.response.ReviewCommentResponse;
import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import com.univent.service.FlaggedContentService;
import com.univent.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;
    private final FlaggedContentService flaggedContentService;
    @PostMapping
    public ResponseEntity<ReviewResponse> submitReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ReviewSubmitRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        ReviewResponse response = reviewService.submitReview(user, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<ReviewResponse>> getReviews(
            @RequestParam UUID collegeId,
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) String sortBy,
            @PageableDefault(size = 20) Pageable pageable) {
        // Only show PUBLISHED reviews to public
        return ResponseEntity.ok(reviewService.getPublishedReviewsByCollege(collegeId, programId, sortBy, pageable));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewResponse> getReview(@PathVariable UUID reviewId) {
        return ResponseEntity.ok(reviewService.getReviewById(reviewId));
    }

    @PostMapping("/{reviewId}/vote")
    public ResponseEntity<Void> voteReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewVoteRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        reviewService.voteReview(user, reviewId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{reviewId}/comments")
    public ResponseEntity<ReviewCommentResponse> addComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId,
            @Valid @RequestBody ReviewCommentRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        ReviewCommentResponse response = reviewService.addComment(user, reviewId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reviewId}/comments")
    public ResponseEntity<Page<ReviewCommentResponse>> getComments(
            @PathVariable UUID reviewId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getCommentsByReview(reviewId, pageable));
    }

    @PostMapping("/{reviewId}/flag")
    public ResponseEntity<Void> flagReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID reviewId,
            @Valid @RequestBody FlagContentRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        flaggedContentService.flagReview(user, reviewId, request.getReason());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments/{commentId}/flag")
    public ResponseEntity<Void> flagComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID commentId,
            @Valid @RequestBody FlagContentRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        flaggedContentService.flagComment(user, commentId, request.getReason());
        return ResponseEntity.ok().build();
    }
}