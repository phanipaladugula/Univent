package com.univent.service;

import com.univent.model.dto.request.CollegeProgramRequest;
import com.univent.model.dto.response.CollegeProgramResponse;
import com.univent.model.dto.response.CollegeResponse;
import com.univent.model.dto.response.ProgramResponse;
import com.univent.model.entity.College;
import com.univent.model.entity.CollegeProgram;
import com.univent.model.entity.Program;
import com.univent.repository.CollegeProgramRepository;
import com.univent.repository.CollegeRepository;
import com.univent.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollegeProgramService {

    private final CollegeProgramRepository collegeProgramRepository;
    private final CollegeRepository collegeRepository;
    private final ProgramRepository programRepository;
    private final CollegeService collegeService;
    private final ProgramService programService;

    @Transactional
    public CollegeProgramResponse mapProgramToCollege(CollegeProgramRequest request) {
        College college = collegeRepository.findById(request.getCollegeId())
                .orElseThrow(() -> new RuntimeException("College not found"));
        Program program = programRepository.findById(request.getProgramId())
                .orElseThrow(() -> new RuntimeException("Program not found"));

        // Check if mapping already exists
        if (collegeProgramRepository.findByCollegeAndProgram(college, program).isPresent()) {
            throw new RuntimeException("Program already mapped to this college");
        }

        CollegeProgram collegeProgram = new CollegeProgram();
        collegeProgram.setCollege(college);
        collegeProgram.setProgram(program);
        collegeProgram.setFeesTotal(request.getFeesTotal());
        collegeProgram.setFeesPerYear(request.getFeesPerYear());
        collegeProgram.setSeatsIntake(request.getSeatsIntake());
        collegeProgram.setEntranceExam(request.getEntranceExam());
        collegeProgram.setCutoffRank(request.getCutoffRank());
        collegeProgram.setMedianPackage(request.getMedianPackage());
        collegeProgram.setHighestPackage(request.getHighestPackage());
        collegeProgram.setPlacementPercentage(request.getPlacementPercentage());
        collegeProgram.setAverageRating(java.math.BigDecimal.ZERO);
        collegeProgram.setTotalReviews(0);

        CollegeProgram saved = collegeProgramRepository.save(collegeProgram);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CollegeProgramResponse> getProgramsByCollege(UUID collegeId) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));

        return collegeProgramRepository.findByCollege(college).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CollegeProgramResponse> getCollegesByProgram(UUID programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found"));

        return collegeProgramRepository.findByProgram(program).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeProgramFromCollege(UUID collegeId, UUID programId) {
        College college = collegeRepository.findById(collegeId)
                .orElseThrow(() -> new RuntimeException("College not found"));
        Program program = programRepository.findById(programId)
                .orElseThrow(() -> new RuntimeException("Program not found"));

        CollegeProgram collegeProgram = collegeProgramRepository.findByCollegeAndProgram(college, program)
                .orElseThrow(() -> new RuntimeException("Mapping not found"));

        collegeProgramRepository.delete(collegeProgram);
    }

    private CollegeProgramResponse mapToResponse(CollegeProgram cp) {
        return CollegeProgramResponse.builder()
                .id(cp.getId())
                .college(collegeService.mapToResponse(cp.getCollege()))
                .program(programService.mapToResponse(cp.getProgram()))
                .feesTotal(cp.getFeesTotal())
                .feesPerYear(cp.getFeesPerYear())
                .seatsIntake(cp.getSeatsIntake())
                .entranceExam(cp.getEntranceExam())
                .cutoffRank(cp.getCutoffRank())
                .medianPackage(cp.getMedianPackage())
                .highestPackage(cp.getHighestPackage())
                .placementPercentage(cp.getPlacementPercentage())
                .averageRating(cp.getAverageRating())
                .totalReviews(cp.getTotalReviews())
                .build();
    }
}