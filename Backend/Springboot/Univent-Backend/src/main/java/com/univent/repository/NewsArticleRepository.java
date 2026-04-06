package com.univent.repository;

import com.univent.model.entity.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, UUID> {
    Optional<NewsArticle> findByArticleUrl(String articleUrl);

    Page<NewsArticle> findByOrderByPublishedAtDesc(Pageable pageable);

    Page<NewsArticle> findByCollegeIdOrderByPublishedAtDesc(UUID collegeId, Pageable pageable);

    @Modifying
    @Query("UPDATE NewsArticle n SET n.upvotes = n.upvotes + 1 WHERE n.id = :articleId")
    void incrementUpvotes(UUID articleId);

    long countByScrapedAtAfter(LocalDateTime since);
}