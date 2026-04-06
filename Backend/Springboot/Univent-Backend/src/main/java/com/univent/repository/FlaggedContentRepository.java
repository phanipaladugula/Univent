package com.univent.repository;

import com.univent.model.entity.FlaggedContent;
import com.univent.model.entity.Review;
import com.univent.model.entity.ReviewComment;
import com.univent.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlaggedContentRepository extends JpaRepository<FlaggedContent, UUID> {

    Page<FlaggedContent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Optional<FlaggedContent> findByReviewAndFlaggedBy(Review review, User flaggedBy);

    Optional<FlaggedContent> findByCommentAndFlaggedBy(ReviewComment comment, User flaggedBy);

    boolean existsByReviewAndFlaggedBy(Review review, User flaggedBy);

    boolean existsByCommentAndFlaggedBy(ReviewComment comment, User flaggedBy);

    @Modifying
    @Query("UPDATE FlaggedContent f SET f.status = :status, f.resolvedBy = :resolvedBy, " +
            "f.resolvedAt = CURRENT_TIMESTAMP WHERE f.id = :flagId")
    void updateStatus(UUID flagId, String status, User resolvedBy);

    long countByStatus(String status);
}