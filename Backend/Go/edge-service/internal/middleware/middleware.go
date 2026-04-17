package middleware

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	"github.com/redis/go-redis/v9"
)

// ─── Rate Limiter ────────────────────────────────────

type RateLimitConfig struct {
	Limit  int
	Window time.Duration
}

type RateLimiter struct {
	redis *redis.Client
	local map[string]*localBucket
	mu    sync.RWMutex
	rules map[string]RateLimitConfig
}

type localBucket struct {
	tokens    int
	lastReset time.Time
	limit     int
	window    time.Duration
}

var rateLimitScript = redis.NewScript(`
local current = redis.call("INCR", KEYS[1])
if current == 1 then
  redis.call("PEXPIRE", KEYS[1], ARGV[1])
end
local ttl = redis.call("PTTL", KEYS[1])
return {current, ttl}
`)

func NewRateLimiter(redisClient *redis.Client) *RateLimiter {
	rl := &RateLimiter{
		redis: redisClient,
		local: make(map[string]*localBucket),
		rules: map[string]RateLimitConfig{
			"GET:/api/v1/":                 {Limit: 120, Window: time.Minute},
			"POST:/api/v1/auth/register":   {Limit: 5, Window: 15 * time.Minute},
			"POST:/api/v1/auth/verify":     {Limit: 10, Window: 15 * time.Minute},
			"POST:/api/v1/reviews":         {Limit: 3, Window: 24 * time.Hour},
			"POST:/api/v1/reviews/comment": {Limit: 30, Window: time.Minute},
			"POST:/api/v1/ai/chat":         {Limit: 20, Window: time.Hour},
			"POST:/api/v1/news/posts":      {Limit: 5, Window: time.Hour},
			"POST:/api/v1/flag":            {Limit: 10, Window: time.Hour},
			"DEFAULT":                      {Limit: 60, Window: time.Minute},
		},
	}

	go func() {
		ticker := time.NewTicker(5 * time.Minute)
		defer ticker.Stop()
		for range ticker.C {
			rl.cleanup()
		}
	}()

	return rl
}

func (rl *RateLimiter) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		clientKey := rl.getClientKey(r)
		rule := rl.matchRule(r)

		allowed, remaining, resetAt := rl.checkAllowed(r.Context(), clientKey, rule)

		w.Header().Set("X-RateLimit-Limit", fmt.Sprintf("%d", rule.Limit))
		w.Header().Set("X-RateLimit-Remaining", fmt.Sprintf("%d", remaining))
		w.Header().Set("X-RateLimit-Reset", fmt.Sprintf("%d", resetAt))

		if !allowed {
			resetIn := maxInt64(1, resetAt-time.Now().Unix())
			w.Header().Set("Retry-After", fmt.Sprintf("%d", resetIn))
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(http.StatusTooManyRequests)
			_, _ = w.Write([]byte(`{"success":false,"error":"Too many requests. Please try again later."}`))
			return
		}

		next.ServeHTTP(w, r)
	})
}

func (rl *RateLimiter) getClientKey(r *http.Request) string {
	ip := getClientIP(r)
	path := normalizeRoutePattern(r)
	method := r.Method
	return fmt.Sprintf("rl:%s:%s:%s", method, path, ip)
}

func (rl *RateLimiter) matchRule(r *http.Request) RateLimitConfig {
	path := normalizeRoutePattern(r)
	method := r.Method
	key := method + ":" + path

	if rule, ok := rl.rules[key]; ok {
		return rule
	}

	for pattern, rule := range rl.rules {
		if pattern == "DEFAULT" {
			continue
		}
		parts := strings.SplitN(pattern, ":", 2)
		if len(parts) == 2 && parts[0] == method && strings.HasPrefix(path, parts[1]) {
			return rule
		}
	}

	return rl.rules["DEFAULT"]
}

func (rl *RateLimiter) checkAllowed(ctx context.Context, key string, rule RateLimitConfig) (allowed bool, remaining int, resetAt int64) {
	if rl.redis != nil {
		allowed, remaining, resetAt, err := rl.checkRedis(ctx, key, rule)
		if err == nil {
			return allowed, remaining, resetAt
		}
		log.Printf("rate limiter redis fallback for key=%s error=%v", key, err)
	}

	return rl.checkLocal(key, rule)
}

func (rl *RateLimiter) checkRedis(ctx context.Context, key string, rule RateLimitConfig) (bool, int, int64, error) {
	result, err := rateLimitScript.Run(ctx, rl.redis, []string{key}, rule.Window.Milliseconds()).Result()
	if err != nil {
		return false, 0, 0, err
	}

	values, ok := result.([]interface{})
	if !ok || len(values) != 2 {
		return false, 0, 0, fmt.Errorf("unexpected redis rate-limit result: %T", result)
	}

	current, err := toInt64(values[0])
	if err != nil {
		return false, 0, 0, err
	}
	ttlMs, err := toInt64(values[1])
	if err != nil {
		return false, 0, 0, err
	}

	if ttlMs < 0 {
		ttlMs = rule.Window.Milliseconds()
	}

	remaining := rule.Limit - int(current)
	if remaining < 0 {
		remaining = 0
	}
	resetAt := time.Now().Add(time.Duration(ttlMs) * time.Millisecond).Unix()

	return current <= int64(rule.Limit), remaining, resetAt, nil
}

func (rl *RateLimiter) checkLocal(key string, rule RateLimitConfig) (allowed bool, remaining int, resetAt int64) {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	bucket, exists := rl.local[key]
	now := time.Now()

	if !exists || now.Sub(bucket.lastReset) > rule.Window {
		rl.local[key] = &localBucket{
			tokens:    rule.Limit - 1,
			lastReset: now,
			limit:     rule.Limit,
			window:    rule.Window,
		}
		return true, rule.Limit - 1, now.Add(rule.Window).Unix()
	}

	if bucket.tokens <= 0 {
		return false, 0, bucket.lastReset.Add(rule.Window).Unix()
	}

	bucket.tokens--
	return true, bucket.tokens, bucket.lastReset.Add(rule.Window).Unix()
}

func (rl *RateLimiter) cleanup() {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	now := time.Now()
	for key, bucket := range rl.local {
		if now.Sub(bucket.lastReset) > bucket.window*2 {
			delete(rl.local, key)
		}
	}
}

func normalizeRoutePattern(r *http.Request) string {
	if routeCtx := chi.RouteContext(r.Context()); routeCtx != nil {
		if pattern := routeCtx.RoutePattern(); pattern != "" {
			return pattern
		}
	}

	path := r.URL.Path
	if path == "" {
		return "/"
	}
	if len(path) > 80 {
		return path[:80]
	}
	return path
}

func toInt64(v interface{}) (int64, error) {
	switch value := v.(type) {
	case int64:
		return value, nil
	case int:
		return int64(value), nil
	case string:
		var parsed int64
		_, err := fmt.Sscan(value, &parsed)
		return parsed, err
	default:
		return 0, fmt.Errorf("unsupported numeric type %T", v)
	}
}

func maxInt64(a, b int64) int64 {
	if a > b {
		return a
	}
	return b
}

// ─── Device Fingerprinting ──────────────────────────

type FingerprintMiddleware struct{}

func NewFingerprintMiddleware() *FingerprintMiddleware {
	return &FingerprintMiddleware{}
}

func (fm *FingerprintMiddleware) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		fp := GenerateFingerprint(r)
		r.Header.Set("X-Device-Fingerprint", fp)
		next.ServeHTTP(w, r)
	})
}

func GenerateFingerprint(r *http.Request) string {
	data := strings.Join([]string{
		r.Header.Get("User-Agent"),
		r.Header.Get("Accept-Language"),
		getClientIP(r),
		r.Header.Get("X-Screen-Resolution"),
		r.Header.Get("X-Timezone-Offset"),
	}, "|")

	hash := sha256.Sum256([]byte(data))
	return hex.EncodeToString(hash[:])
}

func getClientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[0])
	}

	if xri := r.Header.Get("X-Real-IP"); xri != "" {
		return xri
	}

	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

// ─── Logging Middleware ──────────────────────────────

func LoggingMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		wrapped := &responseWriter{ResponseWriter: w, statusCode: http.StatusOK}

		next.ServeHTTP(wrapped, r)

		requestID := chimw.GetReqID(r.Context())
		log.Printf("method=%s path=%s route=%s client_ip=%s status=%d duration=%s request_id=%s",
			r.Method, r.URL.Path, normalizeRoutePattern(r), getClientIP(r),
			wrapped.statusCode, time.Since(start), requestID)
	})
}

type responseWriter struct {
	http.ResponseWriter
	statusCode int
}

func (rw *responseWriter) WriteHeader(code int) {
	rw.statusCode = code
	rw.ResponseWriter.WriteHeader(code)
}
