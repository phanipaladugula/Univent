package model

import (
	"time"

	"github.com/google/uuid"
)

// ─── Notification ────────────────────────────────────
type Notification struct {
	ID        uuid.UUID         `json:"id"`
	UserID    uuid.UUID         `json:"user_id"`
	Type      string            `json:"type"`
	Title     string            `json:"title"`
	Body      string            `json:"body,omitempty"`
	Data      map[string]string `json:"data,omitempty"`
	IsRead    bool              `json:"is_read"`
	CreatedAt time.Time         `json:"created_at"`
}

type NotificationEvent struct {
	UserID    uuid.UUID         `json:"user_id"`
	Type      string            `json:"type"`
	Title     string            `json:"title"`
	Body      string            `json:"body"`
	Data      map[string]string `json:"data"`
	Timestamp time.Time         `json:"timestamp"`
}

// ─── Audit ───────────────────────────────────────────
type AuditLog struct {
	ID               uuid.UUID              `json:"id"`
	Timestamp        time.Time              `json:"timestamp"`
	ActorID          *uuid.UUID             `json:"actor_id,omitempty"`
	ActorRole        string                 `json:"actor_role,omitempty"`
	ActorIP          string                 `json:"actor_ip,omitempty"`
	ActorFingerprint string                 `json:"actor_fingerprint,omitempty"`
	Action           string                 `json:"action"`
	ResourceType     string                 `json:"resource_type"`
	ResourceID       *uuid.UUID             `json:"resource_id,omitempty"`
	Metadata         map[string]interface{} `json:"metadata,omitempty"`
	CreatedAt        time.Time              `json:"created_at"`
}

type AuditEvent struct {
	ActorID          string                 `json:"actor_id"`
	ActorRole        string                 `json:"actor_role"`
	ActorIP          string                 `json:"actor_ip"`
	ActorFingerprint string                 `json:"actor_fingerprint"`
	Action           string                 `json:"action"`
	ResourceType     string                 `json:"resource_type"`
	ResourceID       string                 `json:"resource_id"`
	Metadata         map[string]interface{} `json:"metadata"`
	Timestamp        time.Time              `json:"timestamp"`
}

// ─── Analytics ───────────────────────────────────────
type AnalyticsDailySnapshot struct {
	Date                  string   `json:"date"`
	TotalUsers            int      `json:"total_users"`
	NewUsers              int      `json:"new_users"`
	ActiveUsers           int      `json:"active_users"`
	ReviewsSubmitted      int      `json:"reviews_submitted"`
	ReviewsPublished      int      `json:"reviews_published"`
	ReviewsRejected       int      `json:"reviews_rejected"`
	ComparisonsMade       int      `json:"comparisons_made"`
	AIChatRequests        int      `json:"ai_chat_requests"`
	VerificationsSubmitted int     `json:"verifications_submitted"`
	VerificationsApproved int      `json:"verifications_approved"`
	AvgSentimentScore     *float64 `json:"avg_sentiment_score,omitempty"`
}

type DashboardResponse struct {
	Period  string            `json:"period"`
	Metrics DashboardMetrics  `json:"metrics"`
	Trends  DashboardTrends   `json:"trends"`
}

type DashboardMetrics struct {
	TotalUsers          int     `json:"total_users"`
	ActiveUsersToday    int     `json:"active_users_today"`
	ReviewsToday        int     `json:"reviews_today"`
	ReviewsPending      int     `json:"reviews_pending"`
	AvgReviewSentiment  float64 `json:"avg_review_sentiment"`
	ComparisonsToday    int     `json:"comparisons_today"`
	AIChatRequestsToday int     `json:"ai_chat_requests_today"`
	VerificationsPending int    `json:"verifications_pending"`
	FlagsPending        int     `json:"flags_pending"`
}

type DashboardTrends struct {
	Reviews7D []int `json:"reviews_7d"`
	Users7D   []int `json:"users_7d"`
}

// ─── Device Fingerprint ──────────────────────────────
type DeviceFingerprint struct {
	ID              uuid.UUID  `json:"id"`
	FingerprintHash string     `json:"fingerprint_hash"`
	UserID          *uuid.UUID `json:"user_id,omitempty"`
	UserAgent       string     `json:"user_agent,omitempty"`
	IPAddress       string     `json:"ip_address,omitempty"`
	FirstSeenAt     time.Time  `json:"first_seen_at"`
	LastSeenAt      time.Time  `json:"last_seen_at"`
	RequestCount    int        `json:"request_count"`
	IsFlagged       bool       `json:"is_flagged"`
}

// ─── Health ──────────────────────────────────────────
type HealthResponse struct {
	Status   string                   `json:"status"`
	Services map[string]ServiceHealth `json:"services"`
	Uptime   int64                    `json:"uptime_seconds"`
	Version  string                   `json:"version"`
}

type ServiceHealth struct {
	Status    string `json:"status"`
	LatencyMs int64  `json:"latency_ms"`
}

// ─── WebSocket ───────────────────────────────────────
type WSMessage struct {
	Type string      `json:"type"`
	Data interface{} `json:"data"`
}

// ─── API Responses ───────────────────────────────────
type APIResponse struct {
	Success bool        `json:"success"`
	Data    interface{} `json:"data,omitempty"`
	Error   string      `json:"error,omitempty"`
	Meta    *PageMeta   `json:"meta,omitempty"`
}

type PageMeta struct {
	Page       int `json:"page"`
	PageSize   int `json:"page_size"`
	TotalItems int `json:"total_items"`
	TotalPages int `json:"total_pages"`
}
