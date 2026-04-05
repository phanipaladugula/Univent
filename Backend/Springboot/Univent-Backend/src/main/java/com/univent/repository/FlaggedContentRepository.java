package com.univent.repository;

import com.univent.model.entity.FlaggedContent;
import com.univent.model.entity.Review;
import com.univent.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlaggedContentRepository extends JpaRepository<FlaggedContent, UUID> {
    Page<FlaggedContent> findByStatus(String status, Pageable pageable);
    Optional<FlaggedContent> findByReviewAndFlaggedBy(Review review, User flaggedBy);
    boolean existsByReviewAndFlaggedBy(Review review, User flaggedBy);
}