package com.univent.service;

import com.univent.model.dto.request.OtpVerificationRequest;
import com.univent.model.dto.request.RegisterRequest;
import com.univent.model.dto.response.AuthResponse;
import com.univent.model.dto.response.UserResponse;
import com.univent.model.entity.User;
import com.univent.model.enums.Role;
import com.univent.model.enums.VerificationStatus;
import com.univent.repository.UserRepository;
import com.univent.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final JwtTokenProvider tokenProvider;

    private static final String[] ADJECTIVES = {"Swift", "Calm", "Brave", "Witty", "Clever", "Bold", "Mighty", "Silent"};
    private static final String[] NOUNS = {"Panda", "Eagle", "Tiger", "Wolf", "Falcon", "Shark", "Dragon", "Phoenix"};

    @Transactional
    public void sendRegistrationOtp(RegisterRequest request) {
        String emailHash = hashEmail(request.getEmail());

        if (!userRepository.existsByEmailHash(emailHash)) {
            User user = new User();
            user.setEmailHash(emailHash);
            user.setAnonymousUsername(generateAnonymousUsername());
            user.setAvatarColor(generateAvatarColor());
            user.setRole(Role.USER);
            user.setVerificationStatus(VerificationStatus.UNVERIFIED);
            user.setVerifiedBadge(false);
            user.setReputation(0);
            user.setTotalReviews(0);
            userRepository.save(user);
        }

        String otp = otpService.generateAndStoreOtp(request.getEmail());
        emailService.sendOtpEmail(request.getEmail(), otp);
    }

    @Transactional
    public AuthResponse verifyOtpAndLogin(OtpVerificationRequest request) {
        if (!otpService.validateOtp(request.getEmail(), request.getOtp())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        String emailHash = hashEmail(request.getEmail());
        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setLastActiveAt(LocalDateTime.now());
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getId(), request.getEmail(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .user(mapToUserResponse(user))
                .build();
    }

    public AuthResponse refreshAccessToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String tokenType = tokenProvider.getTokenType(refreshToken);
        if (!"refresh".equals(tokenType)) {
            throw new RuntimeException("Invalid token type");
        }

        UUID userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String newAccessToken = tokenProvider.generateAccessTokenFromHash(userId, user.getEmailHash(), user.getRole().name());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900000L)
                .user(mapToUserResponse(user))
                .build();
    }

    private String hashEmail(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().trim().getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing email", e);
        }
    }

    private String generateAnonymousUsername() {
        String adjective = ADJECTIVES[(int) (Math.random() * ADJECTIVES.length)];
        String noun = NOUNS[(int) (Math.random() * NOUNS.length)];
        int number = (int) (Math.random() * 100);
        String username = adjective + noun + number;

        while (userRepository.existsByAnonymousUsername(username)) {
            number = (int) (Math.random() * 1000);
            username = adjective + noun + number;
        }
        return username;
    }

    private String generateAvatarColor() {
        String[] colors = {"#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FFEAA7", "#DDA0DD", "#98D8C8", "#F7B787"};
        return colors[(int) (Math.random() * colors.length)];
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .anonymousUsername(user.getAnonymousUsername())
                .avatarColor(user.getAvatarColor())
                .role(user.getRole())
                .verificationStatus(user.getVerificationStatus())
                .verifiedBadge(user.getVerifiedBadge())
                .reputation(user.getReputation())
                .totalReviews(user.getTotalReviews())
                .graduationYear(user.getGraduationYear())
                .lastActiveAt(user.getLastActiveAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}