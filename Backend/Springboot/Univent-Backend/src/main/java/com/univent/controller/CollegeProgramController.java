package com.univent.controller;

import com.univent.model.dto.request.CollegeProgramRequest;
import com.univent.model.dto.response.CollegeProgramResponse;
import com.univent.service.CollegeProgramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/college-programs")
@RequiredArgsConstructor
public class CollegeProgramController {

    private final CollegeProgramService collegeProgramService;

    // Public endpoints
    @GetMapping("/college/{collegeId}")
    public ResponseEntity<List<CollegeProgramResponse>> getProgramsByCollege(@PathVariable UUID collegeId) {
        return ResponseEntity.ok(collegeProgramService.getProgramsByCollege(collegeId));
    }

    @GetMapping("/program/{programId}")
    public ResponseEntity<List<CollegeProgramResponse>> getCollegesByProgram(@PathVariable UUID programId) {
        return ResponseEntity.ok(collegeProgramService.getCollegesByProgram(programId));
    }

    // Admin only endpoints
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollegeProgramResponse> mapProgramToCollege(@Valid @RequestBody CollegeProgramRequest request) {
        CollegeProgramResponse response = collegeProgramService.mapProgramToCollege(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/college/{collegeId}/program/{programId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeProgramFromCollege(
            @PathVariable UUID collegeId,
            @PathVariable UUID programId) {
        collegeProgramService.removeProgramFromCollege(collegeId, programId);
        return ResponseEntity.noContent().build();
    }
}