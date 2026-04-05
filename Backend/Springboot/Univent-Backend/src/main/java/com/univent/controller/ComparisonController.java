package com.univent.controller;

import com.univent.model.dto.request.SaveComparisonRequest;
import com.univent.model.dto.response.CollegeComparisonResponse;
import com.univent.model.dto.response.SavedComparisonResponse;
import com.univent.model.entity.SavedComparison;
import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import com.univent.service.ComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/compare")
@RequiredArgsConstructor
@Slf4j
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final UserRepository userRepository;

    @PostMapping("/colleges")
    public ResponseEntity<CollegeComparisonResponse> compareColleges(
            @RequestParam List<UUID> collegeIds,
            @RequestParam UUID programId) {
        log.info("Comparing colleges: {} for program: {}", collegeIds, programId);
        CollegeComparisonResponse response = comparisonService.compareColleges(collegeIds, programId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    public ResponseEntity<SavedComparisonResponse> saveComparison(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SaveComparisonRequest request) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        SavedComparison saved = comparisonService.saveComparison(
                user, request.getName(), request.getCollegeIds(), request.getProgramId());

        return ResponseEntity.ok(mapToResponse(saved));
    }

    @GetMapping("/saved")
    public ResponseEntity<Page<SavedComparisonResponse>> getSavedComparisons(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(comparisonService.getUserSavedComparisons(user, pageable)
                .map(this::mapToResponse));
    }

    @DeleteMapping("/saved/{comparisonId}")
    public ResponseEntity<Void> deleteSavedComparison(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID comparisonId) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        comparisonService.deleteSavedComparison(user, comparisonId);
        return ResponseEntity.noContent().build();
    }

    private SavedComparisonResponse mapToResponse(SavedComparison saved) {
        return SavedComparisonResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .collegeIds(List.of(saved.getCollegeIds()))
                .programId(saved.getProgram().getId())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}