package com.univent.controller;

import com.univent.model.entity.User;
import com.univent.model.enums.VerificationStatus;
import com.univent.repository.UserRepository;
import com.univent.service.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/verification")
@RequiredArgsConstructor
@Slf4j
public class VerificationController {

    private final VerificationService verificationService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadIdCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        verificationService.uploadIdCard(user, file);

        Map<String, String> response = new HashMap<>();
        response.put("message", "ID card uploaded successfully. Verification pending.");
        response.put("status", "ID_PENDING");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getVerificationStatus(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("verificationStatus", user.getVerificationStatus());
        response.put("verifiedBadge", user.getVerifiedBadge());
        response.put("verificationRequestedAt", user.getVerificationRequestedAt());

        if (user.getVerificationRejectionReason() != null) {
            response.put("rejectionReason", user.getVerificationRejectionReason());
        }

        return ResponseEntity.ok(response);
    }
}

// Admin endpoints
@RestController
@RequestMapping("/api/v1/admin/verification")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
class AdminVerificationController {

    private final VerificationService verificationService;
    private final UserRepository userRepository;

    @GetMapping("/pending")
    public ResponseEntity<Page<User>> getPendingVerifications(Pageable pageable) {
        return ResponseEntity.ok(userRepository.findByVerificationStatus(
                VerificationStatus.ID_PENDING, pageable));
    }

    @GetMapping("/{userId}/id-card")
    public ResponseEntity<Map<String, String>> viewIdCard(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        String url = verificationService.getDecryptedIdCardUrl(admin, userId);

        Map<String, String> response = new HashMap<>();
        response.put("url", url);
        response.put("expiresIn", "5 minutes");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/approve")
    public ResponseEntity<Map<String, String>> approveVerification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        verificationService.approveVerification(userId, admin);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Verification approved successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/reject")
    public ResponseEntity<Map<String, String>> rejectVerification(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID userId,
            @RequestParam String reason) {
        User admin = userRepository.findByEmailHash(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        verificationService.rejectVerification(userId, admin, reason);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Verification rejected");
        return ResponseEntity.ok(response);
    }
}