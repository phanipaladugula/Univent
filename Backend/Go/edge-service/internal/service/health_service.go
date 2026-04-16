package service

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/store"
)

type HealthService struct {
	pg           *store.PostgresStore
	redis        *store.RedisStore
	springBootURL string
	pythonAIURL  string
	startTime    time.Time
}

func NewHealthService(pg *store.PostgresStore, redis *store.RedisStore, springBootURL, pythonAIURL string) *HealthService {
	return &HealthService{
		pg:           pg,
		redis:        redis,
		springBootURL: springBootURL,
		pythonAIURL:  pythonAIURL,
		startTime:    time.Now(),
	}
}

func (hs *HealthService) CheckHealth(ctx context.Context) model.HealthResponse {
	services := make(map[string]model.ServiceHealth)

	// PostgreSQL
	services["postgresql"] = hs.checkPostgres(ctx)

	// Redis
	services["redis"] = hs.checkRedis(ctx)

	// Spring Boot
	services["spring_boot"] = hs.checkHTTP(ctx, hs.springBootURL+"/actuator/health")

	// Python AI
	services["python_ai"] = hs.checkHTTP(ctx, hs.pythonAIURL+"/health")

	// Overall status
	overallStatus := "healthy"
	for _, svc := range services {
		if svc.Status == "down" {
			overallStatus = "degraded"
			break
		}
	}

	return model.HealthResponse{
		Status:   overallStatus,
		Services: services,
		Uptime:   int64(time.Since(hs.startTime).Seconds()),
		Version:  "1.0.0",
	}
}

func (hs *HealthService) checkPostgres(ctx context.Context) model.ServiceHealth {
	start := time.Now()
	err := hs.pg.Ping(ctx)
	latency := time.Since(start).Milliseconds()

	if err != nil {
		return model.ServiceHealth{Status: "down", LatencyMs: latency}
	}
	return model.ServiceHealth{Status: "up", LatencyMs: latency}
}

func (hs *HealthService) checkRedis(ctx context.Context) model.ServiceHealth {
	start := time.Now()
	err := hs.redis.Ping(ctx)
	latency := time.Since(start).Milliseconds()

	if err != nil {
		return model.ServiceHealth{Status: "down", LatencyMs: latency}
	}
	return model.ServiceHealth{Status: "up", LatencyMs: latency}
}

func (hs *HealthService) checkHTTP(ctx context.Context, url string) model.ServiceHealth {
	start := time.Now()

	client := &http.Client{Timeout: 3 * time.Second}
	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return model.ServiceHealth{Status: "down", LatencyMs: 0}
	}

	resp, err := client.Do(req)
	latency := time.Since(start).Milliseconds()

	if err != nil {
		log.Printf("⚠️ Health check failed for %s: %v", url, err)
		return model.ServiceHealth{Status: "down", LatencyMs: latency}
	}
	defer resp.Body.Close()

	status := "up"
	if resp.StatusCode >= 500 {
		status = "down"
	} else if resp.StatusCode >= 400 {
		status = "degraded"
	}

	return model.ServiceHealth{Status: status, LatencyMs: latency}
}

// FingerprintService handles device fingerprint tracking
type FingerprintService struct {
	pg *store.PostgresStore
}

func NewFingerprintService(pg *store.PostgresStore) *FingerprintService {
	return &FingerprintService{pg: pg}
}

func (fs *FingerprintService) TrackFingerprint(ctx context.Context, fpHash string, userID *string, userAgent, ip string) error {
	_, err := fs.pg.Pool.Exec(ctx, `
		INSERT INTO device_fingerprints (fingerprint_hash, user_id, user_agent, ip_address, first_seen_at, last_seen_at, request_count)
		VALUES ($1, $2, $3, $4::inet, NOW(), NOW(), 1)
		ON CONFLICT (fingerprint_hash, user_id) DO UPDATE SET
			last_seen_at = NOW(),
			request_count = device_fingerprints.request_count + 1,
			user_agent = EXCLUDED.user_agent,
			ip_address = EXCLUDED.ip_address
	`, fpHash, userID, userAgent, ip)
	return err
}

func (fs *FingerprintService) IsSuspicious(ctx context.Context, fpHash string, collegeID string) (bool, error) {
	var count int
	err := fs.pg.Pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT r.id)
		FROM reviews r
		JOIN device_fingerprints df ON df.user_id = r.user_id
		WHERE df.fingerprint_hash = $1 AND r.college_id::text = $2
	`, fpHash, collegeID).Scan(&count)
	if err != nil {
		return false, err
	}
	return count > 5, nil
}

func (fs *FingerprintService) IsFlagged(ctx context.Context, fpHash string) (bool, error) {
	var flagged bool
	err := fs.pg.Pool.QueryRow(ctx, `
		SELECT COALESCE(is_flagged, FALSE) FROM device_fingerprints WHERE fingerprint_hash = $1 LIMIT 1
	`, fpHash).Scan(&flagged)
	if err != nil {
		return false, nil // Not found = not flagged
	}
	return flagged, nil
}

func (fs *FingerprintService) GetAccountCount(ctx context.Context, fpHash string) (int, error) {
	var count int
	err := fs.pg.Pool.QueryRow(ctx, `
		SELECT COUNT(DISTINCT user_id) FROM device_fingerprints WHERE fingerprint_hash = $1
	`, fpHash).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("count accounts: %w", err)
	}
	return count, nil
}
