package com.univent.repository;

import com.univent.model.entity.SavedComparison;
import com.univent.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SavedComparisonRepository extends JpaRepository<SavedComparison, UUID> {
    Page<SavedComparison> findByUser(User user, Pageable pageable);
    void deleteByUserAndId(User user, UUID id);
}