package com.univent.repository;

import com.univent.model.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {
    Optional<Program> findBySlug(String slug);
    List<Program> findByCategory(String category);
}