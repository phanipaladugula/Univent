package com.univent.repository;

import com.univent.model.entity.Review;
import com.univent.model.entity.ReviewComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewCommentRepository extends JpaRepository<ReviewComment, UUID> {
    Page<ReviewComment> findByReviewAndParentCommentIsNull(Review review, Pageable pageable);
    List<ReviewComment> findByParentCommentId(UUID parentCommentId);
    long countByReview(Review review);
}