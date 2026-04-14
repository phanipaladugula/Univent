package com.univent.repository;

import com.univent.model.entity.College;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollegeRepository extends JpaRepository<College, UUID> {
    Optional<College> findBySlug(String slug);

    // ADD THIS METHOD - for slug collision checking
    boolean existsBySlug(String slug);

    Page<College> findByNameContainingIgnoreCaseOrCityContainingIgnoreCaseOrStateContainingIgnoreCase(
            String name, String city, String state, Pageable pageable);
    Page<College> findByState(String state, Pageable pageable);
    Page<College> findByCollegeType(String collegeType, Pageable pageable);
}