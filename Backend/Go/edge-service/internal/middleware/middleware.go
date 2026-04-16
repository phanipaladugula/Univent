package middleware

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	chimw "github.com/go-chi/chi/v5/middleware"
	"github.com/redis/go-redis/v9"
)

// ─── Rate Limiter ────────────────────────────────────

type RateLimitConfig struct {
	Limit  int
	Window time.Duration
}

type RateLimiter struct {
	redis  *redis.Client
	local  map[string]*localBucket
	mu     sync.RWMutex
	rules  map[string]RateLimitConfig
}

type localBucket struct {
	tokens    int
	lastReset time.Time
	limit     int
	window    time.Duration
}

func NewRateLimiter(redisClient *redis.Client) *RateLimiter {
	rl := &RateLimiter{
		redis: redisClient,
		local: make(map[string]*localBucket),
		rules: map[string]RateLimitConfig{
			"GET:/api/v1/":                {Limit: 120, Window: time.Minute},
			"POST:/api/v1/auth/register":  {Limit: 5, Window: 15 * time.Minute},
			"POST:/api/v1/auth/verify":    {Limit: 10, Window: 15 * time.Minute},
			"POST:/api/v1/reviews":        {Limit: 3, Window: 24 * time.Hour},
			"POST:/api/v1/reviews/comment": {Limit: 30, Window: time.Minute},
			"POST:/api/v1/ai/chat":        {Limit: 20, Window: time.Hour},
			"POST:/api/v1/news/posts":     {Limit: 5, Window: time.Hour},
			"POST:/api/v1/flag":           {Limit: 10, Window: time.Hour},
			"DEFAULT":                     {Limit: 60, Window: time.Minute},
		},
	}

	// Periodic cleanup of stale local buckets
	go func() {
		ticker := time.NewTicker(5 * time.Minute)
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

		allowed, remaining, resetAt := rl.checkLocal(clientKey, rule)

		w.Header().Set("X-RateLimit-Limit", fmt.Sprintf("%d", rule.Limit))
		w.Header().Set("X-RateLimit-Remaining", fmt.Sprintf("%d", remaining))
		w.Header().Set("X-RateLimit-Reset", fmt.Sprintf("%d", resetAt))

		if !allowed {
			w.Header().Set("Retry-After", fmt.Sprintf("%d", int(rule.Window.Seconds())))
			http.Error(w, `{"success":false,"error":"Too many requests. Please try again later."}`, http.StatusTooManyRequests)
			return
		}

		next.ServeHTTP(w, r)
	})
}

func (rl *RateLimiter) getClientKey(r *http.Request) string {
	ip := getClientIP(r)
	path := r.URL.Path
	method := r.Method
	return fmt.Sprintf("rl:%s:%s:%s", method, path, ip)
}

func (rl *RateLimiter) matchRule(r *http.Request) RateLimitConfig {
	path := r.URL.Path
	method := r.Method
	key := method + ":" + path

	// Check exact match first
	if rule, ok := rl.rules[key]; ok {
		return rule
	}

	// Check prefix match
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
	// Check X-Forwarded-For header first (from NGINX)
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
		log.Printf("method=%s path=%s client_ip=%s status=%d duration=%s request_id=%s",
			r.Method, r.URL.Path, getClientIP(r),
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
