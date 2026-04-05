package com.univent.service;

import com.univent.model.entity.User;
import com.univent.model.entity.VerificationAuditLog;
import com.univent.model.enums.VerificationStatus;
import com.univent.repository.UserRepository;
import com.univent.repository.VerificationAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationService {

    private final UserRepository userRepository;
    private final MinioService minioService;
    private final EncryptionService encryptionService;
    private final VerificationAuditLogRepository auditLogRepository;

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    @Transactional
    public void uploadIdCard(User user, MultipartFile file) {
        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("File size must be less than 2MB");
        }

        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            throw new RuntimeException("Only image and PDF files are allowed");
        }

        try {
            // Encrypt the file bytes
            byte[] encryptedData = encryptionService.encryptToBytes(file.getBytes());

            // Upload encrypted data to MinIO
            String filePath = minioService.uploadEncryptedData(encryptedData, file.getOriginalFilename(), user.getId());

            // Update user record
            user.setIdCardPath(filePath);
            user.setIdCardUploadedAt(LocalDateTime.now());
            user.setVerificationStatus(VerificationStatus.ID_PENDING);  // Use ID_PENDING
            user.setVerificationRequestedAt(LocalDateTime.now());
            userRepository.save(user);

            // Log the upload
            logAudit(user, null, "UPLOAD", "ID card uploaded for verification");

            log.info("User {} uploaded ID card for verification", user.getAnonymousUsername());

        } catch (IOException e) {
            log.error("Failed to process ID card upload: {}", e.getMessage());
            throw new RuntimeException("Failed to process ID card", e);
        }
    }

    @Transactional(readOnly = true)
    public String getDecryptedIdCardUrl(User admin, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getIdCardPath() == null) {
            throw new RuntimeException("No ID card found for this user");
        }

        // Log admin access
        logAudit(user, admin, "VIEW", "Admin viewed ID card");

        // Get presigned URL (valid for 5 minutes)
        return minioService.getPresignedUrl(user.getIdCardPath(), 5);
    }

    @Transactional
    public void approveVerification(UUID userId, User admin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getVerificationStatus() != VerificationStatus.ID_PENDING) {
            throw new RuntimeException("User is not pending verification");
        }

        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setVerifiedBadge(true);
        user.setVerificationReviewedBy(admin.getId());
        user.setVerificationReviewedAt(LocalDateTime.now());
        userRepository.save(user);

        // Log the approval
        logAudit(user, admin, "APPROVE", "ID card verification approved");

        log.info("Admin {} approved verification for user {}",
                admin.getAnonymousUsername(), user.getAnonymousUsername());
    }

    @Transactional
    public void rejectVerification(UUID userId, User admin, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getVerificationStatus() != VerificationStatus.ID_PENDING) {
            throw new RuntimeException("User is not pending verification");
        }

        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setVerifiedBadge(false);
        user.setVerificationRejectionReason(reason);
        user.setVerificationReviewedBy(admin.getId());
        user.setVerificationReviewedAt(LocalDateTime.now());
        userRepository.save(user);

        // Log the rejection
        logAudit(user, admin, "REJECT", "ID card verification rejected: " + reason);

        log.info("Admin {} rejected verification for user {}: {}",
                admin.getAnonymousUsername(), user.getAnonymousUsername(), reason);
    }

    private void logAudit(User user, User admin, String action, String details) {
        VerificationAuditLog auditLog = new VerificationAuditLog();
        auditLog.setUser(user);
        auditLog.setAdminId(admin != null ? admin.getId() : null);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(auditLog);
    }
}