package com.univent.service;

import com.univent.model.dto.request.FlagContentRequest;
import com.univent.model.dto.request.ReviewCommentRequest;
import com.univent.model.dto.request.ReviewSubmitRequest;
import com.univent.model.dto.request.ReviewVoteRequest;
import com.univent.model.dto.response.CollegeResponse;
import com.univent.model.dto.response.ProgramResponse;
import com.univent.model.dto.response.ReviewCommentResponse;
import com.univent.model.dto.response.ReviewResponse;
import com.univent.model.dto.response.UserResponse;
import com.univent.model.entity.*;
import com.univent.model.enums.ReviewStatus;
import com.univent.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewCommentRepository commentRepository;
    private final FlaggedContentRepository flaggedContentRepository;
    private final CollegeRepository collegeRepository;
    private final ProgramRepository programRepository;
    private final UserRepository userRepository;
    private final CollegeProgramRepository collegeProgramRepository;
    private final ReviewVoteRepository reviewVoteRepository;
    @Transactional
    public ReviewResponse submitReview(User user, ReviewSubmitRequest request) {
        College college = collegeRepository.findById(request.getCollegeId())
                .orElseThrow(() -> new RuntimeException("College not found"));
        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new RuntimeException("Program not found"));

        Review review = new Review();
        review.setUser(user);
        review.setCollege(college);
        review.setProgram(program);
        review.setGraduationYear(request.getGraduationYear());
        review.setIsCurrentStudent(request.getIsCurrentStudent());
        review.setOverallRating(request.getOverallRating());
        review.setTeachingQuality(request.getTeachingQuality());
        review.setPlacementSupport(request.getPlacementSupport());
        review.setInfrastructure(request.getInfrastructure());
        review.setHostelLife(request.getHostelLife());
        review.setCampusLife(request.getCampusLife());
        review.setValueForMoney(request.getValueForMoney());
        review.setPros(request.getPros().toArray(new String[0]));
        review.setCons(request.getCons().toArray(new String[0]));
        review.setReviewText(request.getReviewText());
        review.setWouldRecommend(request.getWouldRecommend());
        review.setCgpa(request.getCgpa());
        review.setPlacementPackage(request.getPlacementPackage());
        review.setStatus(ReviewStatus.PENDING);
        review.setIsVerifiedReview(user.getVerifiedBadge());
        review.setUpvotes(0);
        review.setDownvotes(0);

        Review saved = reviewRepository.save(review);

        // Update user's total reviews count
        user.setTotalReviews(user.getTotalReviews() + 1);
        userRepository.save(user);

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByCollege(UUID collegeId, UUID programId,
                                                    String sortBy, Pageable pageable) {
        Pageable sortedPageable = getSortedPageable(sortBy, pageable);

        Page<Review> reviews;
        if (programId != null) {
            reviews = reviewRepository.findByCollegeIdAndProgramIdAndStatus(
                    collegeId, programId, ReviewStatus.PUBLISHED, sortedPageable);
        } else {
            reviews = reviewRepository.findByCollegeIdAndProgramIdAndStatus(
                    collegeId, null, ReviewStatus.PUBLISHED, sortedPageable);
        }

        return reviews.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ReviewResponse getReviewById(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        return mapToResponse(review);
    }

    @Transactional
    public void voteReview(User user, UUID reviewId, ReviewVoteRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        ReviewVoteRepository reviewVoteRepository;
        Optional<ReviewVote> existingVote = reviewVoteRepository.findByReviewAndUser(review, user);

        if (existingVote.isPresent()) {
            ReviewVote vote = existingVote.get();
            String currentVoteType = vote.getVoteType();
            String newVoteType = request.getVoteType().name();

            if (currentVoteType.equals(newVoteType)) {
                // Remove vote if same type (toggle off)
                reviewVoteRepository.delete(vote);
                if ("UP".equals(currentVoteType)) {
                    review.setUpvotes(review.getUpvotes() - 1);
                } else {
                    review.setDownvotes(review.getDownvotes() - 1);
                }
            } else {
                // Change vote from UP to DOWN or vice versa
                vote.setVoteType(newVoteType);
                reviewVoteRepository.save(vote);
                if ("UP".equals(newVoteType)) {
                    review.setUpvotes(review.getUpvotes() + 1);
                    review.setDownvotes(review.getDownvotes() - 1);
                } else {
                    review.setUpvotes(review.getUpvotes() - 1);
                    review.setDownvotes(review.getDownvotes() + 1);
                }
            }
        } else {
            // New vote
            ReviewVote newVote = new ReviewVote();
            newVote.setReview(review);
            newVote.setUser(user);
            newVote.setVoteType(request.getVoteType().name());
            reviewVoteRepository.save(newVote);

            if (request.getVoteType() == ReviewVoteRequest.VoteType.UP) {
                review.setUpvotes(review.getUpvotes() + 1);
            } else {
                review.setDownvotes(review.getDownvotes() + 1);
            }
        }

        reviewRepository.save(review);
    }

    @Transactional
    public ReviewCommentResponse addComment(User user, UUID reviewId, ReviewCommentRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        ReviewComment comment = new ReviewComment();
        comment.setReview(review);
        comment.setUser(user);
        comment.setCommentText(request.getCommentText());
        comment.setUpvotes(0);

        if (request.getParentCommentId() != null) {
            ReviewComment parent = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new RuntimeException("Parent comment not found"));
            comment.setParentComment(parent);
        }

        ReviewComment saved = commentRepository.save(comment);
        return mapToCommentResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<ReviewCommentResponse> getCommentsByReview(UUID reviewId, Pageable pageable) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        return commentRepository.findByReviewAndParentCommentIsNull(review, pageable)
                .map(this::mapToCommentResponse);
    }

    @Transactional
    public void flagReview(User user, UUID reviewId, FlagContentRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        if (flaggedContentRepository.existsByReviewAndFlaggedBy(review, user)) {
            throw new RuntimeException("You have already flagged this review");
        }

        FlaggedContent flagged = new FlaggedContent();
        flagged.setReview(review);
        flagged.setFlaggedBy(user);
        flagged.setReason(request.getReason());
        flagged.setStatus("PENDING");

        flaggedContentRepository.save(flagged);
    }

    @Transactional
    public void updateReviewStatus(UUID reviewId, ReviewStatus status, User admin, String reason) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));

        review.setStatus(status);
        if (status == ReviewStatus.PUBLISHED) {
            review.setPublishedAt(LocalDateTime.now());
            updateCollegeStats(review.getCollege().getId());
        }

        reviewRepository.save(review);
    }

    private void updateCollegeStats(UUID collegeId) {
        BigDecimal avgRating = reviewRepository.calculateAverageRatingForCollege(collegeId);
        Integer totalReviews = reviewRepository.countPublishedReviewsForCollege(collegeId);

        College college = collegeRepository.findById(collegeId).orElse(null);
        if (college != null) {
            college.setAverageRating(avgRating != null ? avgRating : BigDecimal.ZERO);
            college.setTotalReviews(totalReviews != null ? totalReviews : 0);
            collegeRepository.save(college);
        }
    }

    private Pageable getSortedPageable(String sortBy, Pageable pageable) {
        if (sortBy == null) return pageable;

        switch (sortBy) {
            case "newest":
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("createdAt").descending());
            case "helpful":
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("upvotes").descending());
            case "highest":
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("overallRating").descending());
            case "lowest":
                return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by("overallRating").ascending());
            default:
                return pageable;
        }
    }

    private ReviewResponse mapToResponse(Review review) {
        UserResponse userResponse = UserResponse.builder()
                .id(review.getUser().getId())
                .anonymousUsername(review.getUser().getAnonymousUsername())
                .avatarColor(review.getUser().getAvatarColor())
                .verifiedBadge(review.getUser().getVerifiedBadge())
                .reputation(review.getUser().getReputation())
                .build();

        // Create CollegeResponse safely
        CollegeResponse collegeResponse = null;
        if (review.getCollege() != null) {
            collegeResponse = CollegeResponse.builder()
                    .id(review.getCollege().getId())
                    .name(review.getCollege().getName())
                    .slug(review.getCollege().getSlug())
                    .city(review.getCollege().getCity())
                    .state(review.getCollege().getState())
                    .collegeType(review.getCollege().getCollegeType())
                    .isVerified(review.getCollege().getIsVerified())
                    .website(review.getCollege().getWebsite())
                    .emailDomain(review.getCollege().getEmailDomain())
                    .logoUrl(review.getCollege().getLogoUrl())
                    .averageRating(review.getCollege().getAverageRating())
                    .totalReviews(review.getCollege().getTotalReviews())
                    .build();
        }

        // Create ProgramResponse safely
        ProgramResponse programResponse = null;
        if (review.getProgram() != null) {
            programResponse = ProgramResponse.builder()
                    .id(review.getProgram().getId())
                    .name(review.getProgram().getName())
                    .slug(review.getProgram().getSlug())
                    .category(review.getProgram().getCategory())
                    .degree(review.getProgram().getDegree())
                    .durationYears(review.getProgram().getDurationYears())
                    .icon(review.getProgram().getIcon())
                    .build();
        }

        // Handle pros and cons arrays safely
        List<String> prosList = review.getPros() != null ? Arrays.asList(review.getPros()) : List.of();
        List<String> consList = review.getCons() != null ? Arrays.asList(review.getCons()) : List.of();

        return ReviewResponse.builder()
                .id(review.getId())
                .user(userResponse)
                .college(collegeResponse)
                .program(programResponse)
                .graduationYear(review.getGraduationYear())
                .isCurrentStudent(review.getIsCurrentStudent())
                .overallRating(review.getOverallRating())
                .teachingQuality(review.getTeachingQuality())
                .placementSupport(review.getPlacementSupport())
                .infrastructure(review.getInfrastructure())
                .hostelLife(review.getHostelLife())
                .campusLife(review.getCampusLife())
                .valueForMoney(review.getValueForMoney())
                .pros(prosList)
                .cons(consList)
                .reviewText(review.getReviewText())
                .wouldRecommend(review.getWouldRecommend())
                .cgpa(review.getCgpa())
                .placementPackage(review.getPlacementPackage())
                .upvotes(review.getUpvotes())
                .downvotes(review.getDownvotes())
                .status(review.getStatus())
                .isVerifiedReview(review.getIsVerifiedReview())
                .sentiment(review.getSentiment())
                .sentimentScore(review.getSentimentScore())
                .helpfulScore(review.getUpvotes() - review.getDownvotes())
                .createdAt(review.getCreatedAt())
                .publishedAt(review.getPublishedAt())
                .build();
    }

    private ReviewCommentResponse mapToCommentResponse(ReviewComment comment) {
        UserResponse userResponse = UserResponse.builder()
                .id(comment.getUser().getId())
                .anonymousUsername(comment.getUser().getAnonymousUsername())
                .avatarColor(comment.getUser().getAvatarColor())
                .build();

        List<ReviewCommentResponse> replies = null;
        if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
            replies = comment.getReplies().stream()
                    .map(this::mapToCommentResponse)
                    .collect(Collectors.toList());
        }

        return ReviewCommentResponse.builder()
                .id(comment.getId())
                .user(userResponse)
                .commentText(comment.getCommentText())
                .upvotes(comment.getUpvotes())
                .replies(replies)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByStatus(ReviewStatus status, Pageable pageable) {
        return reviewRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }
}