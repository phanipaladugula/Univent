package com.univent.repository;

import com.univent.model.entity.Review;
import com.univent.model.entity.ReviewVote;
import com.univent.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewVoteRepository extends JpaRepository<ReviewVote, UUID> {
    Optional<ReviewVote> findByReviewAndUser(Review review, User user);
    long countByReviewAndVoteType(Review review, String voteType);
    void deleteByReviewAndUser(Review review, User user);
}