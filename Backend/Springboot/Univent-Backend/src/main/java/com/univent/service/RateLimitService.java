package com.univent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<List> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) " +
                    "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
                    "local ttl = redis.call('TTL', KEYS[1]) return {current, ttl}",
            List.class
    );

    public boolean isRateLimited(String key, int maxAttempts, long windowSeconds) {
        try {
            List result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds)
            );

            if (result == null || result.isEmpty()) {
                log.warn("Rate limiter script returned no result for key={}", key);
                return false;
            }

            Number current = (Number) result.get(0);
            boolean limited = current.longValue() > maxAttempts;
            if (limited) {
                log.warn("Rate limit exceeded for key: {}", key);
            }
            return limited;
        } catch (Exception ex) {
            log.error("Rate limiting failed open for key={}", key, ex);
            return false;
        }
    }
}
