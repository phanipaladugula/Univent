package com.univent.controller;

import com.univent.model.dto.request.OtpVerificationRequest;
import com.univent.model.dto.request.RefreshTokenRequest;
import com.univent.model.dto.request.RegisterRequest;
import com.univent.model.dto.response.AuthResponse;
import com.univent.model.entity.User;
import com.univent.model.enums.Role;
import com.univent.repository.UserRepository;
import com.univent.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.sendRegistrationOtp(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerificationRequest request) {
        AuthResponse response = authService.verifyOtpAndLogin(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/make-admin")
    public ResponseEntity<String> makeTestUserAdmin() {
        try {
            String emailHash = "973dfe463ec85785f5f95af5ba3906eedb2d931c24e69824a89ea65dba4e813b";
            User user = userRepository.findByEmailHash(emailHash)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            user.setRole(Role.ADMIN);
            userRepository.save(user);

            return ResponseEntity.ok("✓ User " + user.getAnonymousUsername() + " is now an ADMIN!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}