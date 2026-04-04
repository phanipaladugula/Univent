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
}