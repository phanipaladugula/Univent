package com.univent.controller;

import com.univent.model.dto.request.CollegeRequest;
import com.univent.model.dto.response.CollegeResponse;
import com.univent.service.CollegeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/colleges")
@RequiredArgsConstructor
public class CollegeController {

    private final CollegeService collegeService;

    // Public endpoints
    @GetMapping
    public ResponseEntity<Page<CollegeResponse>> getAllColleges(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(collegeService.getAllColleges(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<CollegeResponse>> searchColleges(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(collegeService.searchColleges(query, pageable));
    }

    @GetMapping("/state/{state}")
    public ResponseEntity<Page<CollegeResponse>> getCollegesByState(
            @PathVariable String state,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(collegeService.getCollegesByState(state, pageable));
    }

    @GetMapping("/type/{collegeType}")
    public ResponseEntity<Page<CollegeResponse>> getCollegesByType(
            @PathVariable String collegeType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(collegeService.getCollegesByType(collegeType, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollegeResponse> getCollegeById(@PathVariable UUID id) {
        return ResponseEntity.ok(collegeService.getCollegeById(id));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<CollegeResponse> getCollegeBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(collegeService.getCollegeBySlug(slug));
    }

    // Admin only endpoints
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollegeResponse> createCollege(@Valid @RequestBody CollegeRequest request) {
        CollegeResponse response = collegeService.createCollege(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CollegeResponse> updateCollege(
            @PathVariable UUID id,
            @Valid @RequestBody CollegeRequest request) {
        return ResponseEntity.ok(collegeService.updateCollege(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCollege(@PathVariable UUID id) {
        collegeService.deleteCollege(id);
        return ResponseEntity.noContent().build();
    }
}