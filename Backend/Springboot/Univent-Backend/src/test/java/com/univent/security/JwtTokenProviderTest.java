package com.univent.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpiration", 900000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpiration", 604800000L);
    }

    @Test
    void shouldStoreUserIdAsSubjectAndPreserveEmailHash() {
        UUID userId = UUID.randomUUID();
        String emailHash = "abcd1234:saltedhash";

        String token = jwtTokenProvider.generateAccessToken(userId, emailHash, "USER");
        Claims claims = ReflectionTestUtils.invokeMethod(jwtTokenProvider, "extractAllClaims", token);

        assertEquals(userId.toString(), claims.getSubject());
        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(emailHash, jwtTokenProvider.getEmailHashFromToken(token));
        assertEquals("USER", jwtTokenProvider.getRoleFromToken(token));
        assertEquals("access", jwtTokenProvider.getTokenType(token));
        assertTrue(jwtTokenProvider.validateToken(token));
    }
}
