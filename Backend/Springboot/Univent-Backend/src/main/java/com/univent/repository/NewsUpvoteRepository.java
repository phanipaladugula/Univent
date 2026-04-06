package com.univent.repository;

import com.univent.model.entity.NewsUpvote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsUpvoteRepository extends JpaRepository<NewsUpvote, UUID> {
    Optional<NewsUpvote> findByNewsArticleIdAndUserId(UUID articleId, UUID userId);
    Optional<NewsUpvote> findByStudentPostIdAndUserId(UUID postId, UUID userId);
    boolean existsByNewsArticleIdAndUserId(UUID articleId, UUID userId);
    boolean existsByStudentPostIdAndUserId(UUID postId, UUID userId);
    void deleteByNewsArticleIdAndUserId(UUID articleId, UUID userId);
    void deleteByStudentPostIdAndUserId(UUID postId, UUID userId);
}