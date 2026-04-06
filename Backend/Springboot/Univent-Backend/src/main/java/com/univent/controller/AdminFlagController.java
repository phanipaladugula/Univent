package com.univent.controller;

import com.univent.model.dto.request.ResolveFlagRequest;
import com.univent.model.dto.response.FlaggedContentResponse;
import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import com.univent.service.FlaggedContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/flags")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminFlagController {

    private final FlaggedContentService flaggedContentService;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<Page<FlaggedContentResponse>> getPendingFlags(
            @PageableDefault(size = 20) Pageable pageable) {
        log.info("Admin fetching pending flags");
        return ResponseEntity.ok(flaggedContentService.getPendingFlags(pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getFlagStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("pendingCount", flaggedContentService.getPendingFlagsCount());
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{flagId}/resolve")
    public ResponseEntity<Void> resolveFlag(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID flagId,
            @Valid @RequestBody ResolveFlagRequest request) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        flaggedContentService.resolveFlag(admin, flagId, request);
        return ResponseEntity.ok().build();
    }
}