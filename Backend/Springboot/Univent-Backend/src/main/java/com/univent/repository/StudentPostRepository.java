package com.univent.repository;

import com.univent.model.entity.StudentPost;
import com.univent.model.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StudentPostRepository extends JpaRepository<StudentPost, UUID> {
    Page<StudentPost> findByStatus(PostStatus status, Pageable pageable);

    Page<StudentPost> findByCollegeIdAndStatusOrderByCreatedAtDesc(UUID collegeId, PostStatus status, Pageable pageable);

    Page<StudentPost> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE StudentPost s SET s.upvotes = s.upvotes + 1 WHERE s.id = :postId")
    void incrementUpvotes(UUID postId);
}