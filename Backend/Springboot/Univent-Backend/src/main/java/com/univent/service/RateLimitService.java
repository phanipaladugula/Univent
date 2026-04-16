package com.univent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public boolean isRateLimited(String key, int maxAttempts, long windowSeconds) {
        String countStr = redisTemplate.opsForValue().get(key);
        if (countStr == null) {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
            return false;
        }

        int count = Integer.parseInt(countStr);
        if (count >= maxAttempts) {
            log.warn("Rate limit exceeded for key: {}", key);
            return true;
        }

        redisTemplate.opsForValue().increment(key);
        return false;
    }
}
