package com.univent.repository;

import com.univent.model.entity.College;
import com.univent.model.entity.CollegeProgram;
import com.univent.model.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollegeProgramRepository extends JpaRepository<CollegeProgram, UUID> {
    List<CollegeProgram> findByCollege(College college);
    List<CollegeProgram> findByProgram(Program program);
    Optional<CollegeProgram> findByCollegeAndProgram(College college, Program program);
}