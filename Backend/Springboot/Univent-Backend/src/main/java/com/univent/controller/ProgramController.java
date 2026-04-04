package com.univent.controller;

import com.univent.model.dto.request.ProgramRequest;
import com.univent.model.dto.response.ProgramResponse;
import com.univent.service.ProgramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    // Public endpoints
    @GetMapping
    public ResponseEntity<List<ProgramResponse>> getAllPrograms() {
        return ResponseEntity.ok(programService.getAllPrograms());
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<ProgramResponse>> getProgramsByCategory(@PathVariable String category) {
        return ResponseEntity.ok(programService.getProgramsByCategory(category));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProgramResponse> getProgramById(@PathVariable UUID id) {
        return ResponseEntity.ok(programService.getProgramById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ProgramResponse> getProgramBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(programService.getProgramBySlug(slug));
    }

    // Admin only endpoints
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramResponse> createProgram(@Valid @RequestBody ProgramRequest request) {
        ProgramResponse response = programService.createProgram(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramResponse> updateProgram(
            @PathVariable UUID id,
            @Valid @RequestBody ProgramRequest request) {
        return ResponseEntity.ok(programService.updateProgram(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProgram(@PathVariable UUID id) {
        programService.deleteProgram(id);
        return ResponseEntity.noContent().build();
    }
}