package com.univent.service;

import com.univent.model.dto.request.ResolveFlagRequest;
import com.univent.model.dto.response.FlaggedContentResponse;
import com.univent.model.entity.*;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlaggedContentService {

    private final FlaggedContentRepository flaggedContentRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final UserRepository userRepository;
    private final CollegeService collegeService;
    private final ProgramService programService;

    @Transactional
    public void flagReview(User user, UUID reviewId, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        // Check if user already flagged this review
        if (flaggedContentRepository.existsByReviewAndFlaggedBy(review, user)) {
            throw new RuntimeException("You have already flagged this review");
        }

        // Don't allow flagging own review
        if (review.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You cannot flag your own review");
        }

        FlaggedContent flag = new FlaggedContent();
        flag.setReview(review);
        flag.setFlaggedBy(user);
        flag.setReason(reason);
        flag.setStatus("PENDING");

        flaggedContentRepository.save(flag);
        log.info("User {} flagged review: {} with reason: {}",
                user.getAnonymousUsername(), reviewId, reason);
    }

    @Transactional
    public void flagComment(User user, UUID commentId, String reason) {
        ReviewComment comment = reviewCommentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        // Check if user already flagged this comment
        if (flaggedContentRepository.existsByCommentAndFlaggedBy(comment, user)) {
            throw new RuntimeException("You have already flagged this comment");
        }

        // Don't allow flagging own comment
        if (comment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You cannot flag your own comment");
        }

        FlaggedContent flag = new FlaggedContent();
        flag.setComment(comment);
        flag.setFlaggedBy(user);
        flag.setReason(reason);
        flag.setStatus("PENDING");

        flaggedContentRepository.save(flag);
        log.info("User {} flagged comment: {} with reason: {}",
                user.getAnonymousUsername(), commentId, reason);
    }

    @Transactional(readOnly = true)
    public Page<FlaggedContentResponse> getPendingFlags(Pageable pageable) {
        return flaggedContentRepository.findByStatusOrderByCreatedAtAsc("PENDING", pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public void resolveFlag(User admin, UUID flagId, ResolveFlagRequest request) {
        FlaggedContent flag = flaggedContentRepository.findById(flagId)
                .orElseThrow(() -> new RuntimeException("Flag not found"));

        if (!"PENDING".equals(flag.getStatus())) {
            throw new RuntimeException("This flag has already been resolved");
        }

        if (request.getAction() == ResolveFlagRequest.Action.REMOVE_CONTENT) {
            // Remove the flagged content
            if (flag.getReview() != null) {
                Review review = flag.getReview();
                review.setStatus(ReviewStatus.REMOVED);
                reviewRepository.save(review);
                log.info("Admin {} removed review: {} due to flag",
                        admin.getAnonymousUsername(), review.getId());
            } else if (flag.getComment() != null) {
                reviewCommentRepository.delete(flag.getComment());
                log.info("Admin {} removed comment: {} due to flag",
                        admin.getAnonymousUsername(), flag.getComment().getId());
            }
            flag.setStatus("RESOLVED_REMOVED");
        } else {
            // Dismiss the flag (keep content)
            flag.setStatus("RESOLVED_DISMISSED");
            log.info("Admin {} dismissed flag: {} with reason: {}",
                    admin.getAnonymousUsername(), flagId, request.getReason());
        }

        flag.setResolvedBy(admin);
        flag.setResolvedAt(LocalDateTime.now());
        flaggedContentRepository.save(flag);
    }

    @Transactional(readOnly = true)
    public long getPendingFlagsCount() {
        return flaggedContentRepository.countByStatus("PENDING");
    }

    private FlaggedContentResponse mapToResponse(FlaggedContent flag) {
        FlaggedContentResponse.FlaggedContentResponseBuilder builder = FlaggedContentResponse.builder()
                .id(flag.getId())
                .flaggedByUsername(flag.getFlaggedBy().getAnonymousUsername())
                .flaggedById(flag.getFlaggedBy().getId())
                .reason(flag.getReason())
                .status(flag.getStatus())
                .createdAt(flag.getCreatedAt());

        if (flag.getResolvedBy() != null) {
            builder.resolvedByUsername(flag.getResolvedBy().getAnonymousUsername());
            builder.resolvedAt(flag.getResolvedAt());
        }

        if (flag.getReview() != null) {
            Review review = flag.getReview();
            builder.contentType("REVIEW");
            builder.contentId(review.getId());
            builder.contentSnippet(review.getReviewText().substring(0,
                    Math.min(200, review.getReviewText().length())) + "...");
            builder.college(collegeService.mapToResponse(review.getCollege()));
            builder.program(programService.mapToResponse(review.getProgram()));
            builder.overallRating(review.getOverallRating());
        } else if (flag.getComment() != null) {
            ReviewComment comment = flag.getComment();
            builder.contentType("COMMENT");
            builder.contentId(comment.getId());
            builder.contentSnippet(comment.getCommentText().substring(0,
                    Math.min(200, comment.getCommentText().length())) + "...");
            builder.commentText(comment.getCommentText());
        }

        return builder.build();
    }
}