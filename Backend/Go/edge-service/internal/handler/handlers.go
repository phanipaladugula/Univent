package handler

import (
	"encoding/json"
	"log"
	"math"
	"net/http"
	"strconv"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/gorilla/websocket"

	"github.com/univent/edge-service/internal/middleware"
	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/service"
)

// ─── WebSocket Handler ───────────────────────────────

type WebSocketHandler struct {
	notifService *service.NotificationService
	jwtAuth      *middleware.JWTAuth
	upgrader     websocket.Upgrader
}

func NewWebSocketHandler(ns *service.NotificationService, jwt *middleware.JWTAuth) *WebSocketHandler {
	return &WebSocketHandler{
		notifService: ns,
		jwtAuth:      jwt,
		upgrader: websocket.Upgrader{
			ReadBufferSize:  1024,
			WriteBufferSize: 1024,
			CheckOrigin:     func(r *http.Request) bool { return true }, // Allow all origins in dev
		},
	}
}

func (h *WebSocketHandler) Handle(w http.ResponseWriter, r *http.Request) {
	// Extract token from query param (WebSocket can't send headers)
	token := r.URL.Query().Get("token")
	if token == "" {
		http.Error(w, `{"error":"Missing token parameter"}`, http.StatusUnauthorized)
		return
	}

	// Set it as header for JWT middleware to parse
	r.Header.Set("Authorization", "Bearer "+token)

	conn, err := h.upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("⚠️ WebSocket upgrade failed: %v", err)
		return
	}

	// Parse JWT to get user ID
	userID, ok := middleware.GetUserID(r.Context())
	if !ok {
		conn.WriteMessage(websocket.TextMessage, []byte(`{"error":"Invalid token"}`))
		conn.Close()
		return
	}

	h.notifService.RegisterClient(userID, conn)

	// Read pump (keeps connection alive, handles pings)
	go func() {
		defer h.notifService.UnregisterClient(userID)
		for {
			_, _, err := conn.ReadMessage()
			if err != nil {
				return
			}
		}
	}()
}

// ─── Notification Handler ────────────────────────────

type NotificationHandler struct {
	notifService *service.NotificationService
}

func NewNotificationHandler(ns *service.NotificationService) *NotificationHandler {
	return &NotificationHandler{notifService: ns}
}

func (h *NotificationHandler) GetNotifications(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r.Context())
	if !ok {
		writeError(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}
	pageSize := 20

	notifications, total, err := h.notifService.GetUserNotifications(r.Context(), userID, page, pageSize)
	if err != nil {
		writeError(w, "Failed to fetch notifications", http.StatusInternalServerError)
		return
	}

	writeJSON(w, model.APIResponse{
		Success: true,
		Data:    notifications,
		Meta: &model.PageMeta{
			Page:       page,
			PageSize:   pageSize,
			TotalItems: total,
			TotalPages: int(math.Ceil(float64(total) / float64(pageSize))),
		},
	})
}

func (h *NotificationHandler) MarkAsRead(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r.Context())
	if !ok {
		writeError(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	notifID := chi.URLParam(r, "id")
	if notifID == "" {
		writeError(w, "Missing notification ID", http.StatusBadRequest)
		return
	}

	uid, err := parseUUID(notifID)
	if err != nil {
		writeError(w, "Invalid notification ID", http.StatusBadRequest)
		return
	}

	if err := h.notifService.MarkAsRead(r.Context(), userID, uid); err != nil {
		writeError(w, "Failed to mark as read", http.StatusInternalServerError)
		return
	}

	writeJSON(w, model.APIResponse{Success: true})
}

func (h *NotificationHandler) MarkAllAsRead(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r.Context())
	if !ok {
		writeError(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	if err := h.notifService.MarkAllAsRead(r.Context(), userID); err != nil {
		writeError(w, "Failed to mark all as read", http.StatusInternalServerError)
		return
	}

	writeJSON(w, model.APIResponse{Success: true})
}

func (h *NotificationHandler) GetUnreadCount(w http.ResponseWriter, r *http.Request) {
	userID, ok := middleware.GetUserID(r.Context())
	if !ok {
		writeError(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	count, err := h.notifService.GetUnreadCount(r.Context(), userID)
	if err != nil {
		writeError(w, "Failed to get unread count", http.StatusInternalServerError)
		return
	}

	writeJSON(w, model.APIResponse{
		Success: true,
		Data:    map[string]int{"unread_count": count},
	})
}

// ─── Analytics Handler ───────────────────────────────

type AnalyticsHandler struct {
	analyticsService *service.AnalyticsService
}

func NewAnalyticsHandler(as *service.AnalyticsService) *AnalyticsHandler {
	return &AnalyticsHandler{analyticsService: as}
}

func (h *AnalyticsHandler) GetDashboard(w http.ResponseWriter, r *http.Request) {
	dashboard, err := h.analyticsService.GetDashboard(r.Context())
	if err != nil {
		writeError(w, "Failed to get dashboard", http.StatusInternalServerError)
		return
	}
	writeJSON(w, model.APIResponse{Success: true, Data: dashboard})
}

func (h *AnalyticsHandler) GetReviewsDaily(w http.ResponseWriter, r *http.Request) {
	days, _ := strconv.Atoi(r.URL.Query().Get("days"))
	if days < 1 || days > 90 {
		days = 30
	}

	data, err := h.analyticsService.GetReviewsDaily(r.Context(), days)
	if err != nil {
		writeError(w, "Failed to get review analytics", http.StatusInternalServerError)
		return
	}
	writeJSON(w, model.APIResponse{Success: true, Data: data})
}

func (h *AnalyticsHandler) GetPopularColleges(w http.ResponseWriter, r *http.Request) {
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))
	if limit < 1 || limit > 50 {
		limit = 10
	}

	data, err := h.analyticsService.GetPopularColleges(r.Context(), limit)
	if err != nil {
		writeError(w, "Failed to get popular colleges", http.StatusInternalServerError)
		return
	}
	writeJSON(w, model.APIResponse{Success: true, Data: data})
}

func (h *AnalyticsHandler) GetSentimentBreakdown(w http.ResponseWriter, r *http.Request) {
	data, err := h.analyticsService.GetSentimentBreakdown(r.Context())
	if err != nil {
		writeError(w, "Failed to get sentiment breakdown", http.StatusInternalServerError)
		return
	}
	writeJSON(w, model.APIResponse{Success: true, Data: data})
}

// ─── Audit Handler ───────────────────────────────────

type AuditHandler struct {
	auditService *service.AuditService
}

func NewAuditHandler(as *service.AuditService) *AuditHandler {
	return &AuditHandler{auditService: as}
}

func (h *AuditHandler) GetAuditLogs(w http.ResponseWriter, r *http.Request) {
	action := r.URL.Query().Get("action")
	resourceType := r.URL.Query().Get("resource_type")

	page, _ := strconv.Atoi(r.URL.Query().Get("page"))
	if page < 1 {
		page = 1
	}
	pageSize := 20

	var from, to *time.Time
	if f := r.URL.Query().Get("from"); f != "" {
		if t, err := time.Parse("2006-01-02", f); err == nil {
			from = &t
		}
	}
	if t := r.URL.Query().Get("to"); t != "" {
		if parsed, err := time.Parse("2006-01-02", t); err == nil {
			to = &parsed
		}
	}

	logs, total, err := h.auditService.GetAuditLogs(r.Context(), action, resourceType, from, to, page, pageSize)
	if err != nil {
		writeError(w, "Failed to get audit logs", http.StatusInternalServerError)
		return
	}

	writeJSON(w, model.APIResponse{
		Success: true,
		Data:    logs,
		Meta: &model.PageMeta{
			Page:       page,
			PageSize:   pageSize,
			TotalItems: total,
			TotalPages: int(math.Ceil(float64(total) / float64(pageSize))),
		},
	})
}

// ─── Health Handler ──────────────────────────────────

type HealthHandler struct {
	healthService *service.HealthService
}

func NewHealthHandler(hs *service.HealthService) *HealthHandler {
	return &HealthHandler{healthService: hs}
}

func (h *HealthHandler) GetHealth(w http.ResponseWriter, r *http.Request) {
	health := h.healthService.CheckHealth(r.Context())
	status := http.StatusOK
	if health.Status != "healthy" {
		status = http.StatusServiceUnavailable
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(health)
}

// ─── Helpers ─────────────────────────────────────────

func writeJSON(w http.ResponseWriter, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(data)
}

func writeError(w http.ResponseWriter, msg string, status int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(model.APIResponse{Success: false, Error: msg})
}

func parseUUID(s string) (interface{ String() string }, error) {
	// Simple UUID parse helper
	type uuidStr struct{ val string }
	if len(s) != 36 {
		return nil, http.ErrNotSupported
	}
	return &uuidStr{val: s}, nil
}
