package com.univent.controller;

import com.univent.security.JwtTokenProvider;
import com.univent.model.entity.User;
import com.univent.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor // This generates the constructor for the final fields
public class TestController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @GetMapping("/token")
    public String getTestToken(@RequestParam String email) {
        // Find the user using the hashed email (since your schema uses email_hash)
        User user = userRepository.findByEmailHash(hashEmail(email))
                .orElseThrow(() -> new RuntimeException("User not found. Ensure you ran the manual Docker INSERT."));
        
        return jwtTokenProvider.generateAccessToken(user.getId(), user.getEmailHash(), user.getRole().name());
    }

    @GetMapping("/public")
    public String publicEndpoint() {
        return "This is a public endpoint - no auth required";
    }

    // Helper method to match your Production Hardened schema
    private String hashEmail(String email) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().trim().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing email", e);
        }
    }
}