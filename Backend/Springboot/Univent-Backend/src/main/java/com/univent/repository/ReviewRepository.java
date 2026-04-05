package com.univent.repository;

import com.univent.model.entity.College;
import com.univent.model.entity.Program;
import com.univent.model.entity.Review;
import com.univent.model.entity.User;
import com.univent.model.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {
    Page<Review> findByCollegeAndProgramAndStatus(College college, Program program,
                                                  ReviewStatus status, Pageable pageable);
    Page<Review> findByUserAndStatus(User user, ReviewStatus status, Pageable pageable);
    long countByCollege(College college);

    @Modifying
    @Query("UPDATE Review r SET r.upvotes = r.upvotes + 1 WHERE r.id = :reviewId")
    void incrementUpvotes(@Param("reviewId") UUID reviewId);

    @Modifying
    @Query("UPDATE Review r SET r.downvotes = r.downvotes + 1 WHERE r.id = :reviewId")
    void incrementDownvotes(@Param("reviewId") UUID reviewId);

    // Add these methods to existing ReviewRepository interface

    Page<Review> findByCollegeIdAndProgramIdAndStatus(UUID collegeId, UUID programId,
                                                      ReviewStatus status, Pageable pageable);

    Page<Review> findByUserIdAndStatus(UUID userId, ReviewStatus status, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.college.id = :collegeId AND r.status = :status " +
            "ORDER BY (r.upvotes - r.downvotes) DESC")
    Page<Review> findMostHelpfulByCollege(@Param("collegeId") UUID collegeId,
                                          @Param("status") ReviewStatus status,
                                          Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.college.id = :collegeId AND r.status = :status " +
            "ORDER BY r.overallRating DESC")
    Page<Review> findHighestRatedByCollege(@Param("collegeId") UUID collegeId,
                                           @Param("status") ReviewStatus status,
                                           Pageable pageable);

    @Query("SELECT AVG(r.overallRating) FROM Review r WHERE r.college.id = :collegeId AND r.status = 'PUBLISHED'")
    BigDecimal calculateAverageRatingForCollege(@Param("collegeId") UUID collegeId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.college.id = :collegeId AND r.status = 'PUBLISHED'")
    Integer countPublishedReviewsForCollege(@Param("collegeId") UUID collegeId);

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
}