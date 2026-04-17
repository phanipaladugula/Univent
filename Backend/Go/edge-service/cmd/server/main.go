package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/univent/edge-service/internal/config"
	"github.com/univent/edge-service/internal/handler"
	"github.com/univent/edge-service/internal/kafka"
	"github.com/univent/edge-service/internal/middleware"
	"github.com/univent/edge-service/internal/service"
	"github.com/univent/edge-service/internal/store"
)

func main() {
	log.Println("🚀 Starting Univent Edge Service (Go)")

	cfg := config.Load()

	// ─── Connect to data stores ──────────────────────
	pg, err := store.NewPostgresStore(cfg.PostgresURL)
	if err != nil {
		log.Fatalf("❌ PostgreSQL connection failed: %v", err)
	}
	defer pg.Close()

	redis, err := store.NewRedisStore(cfg.RedisURL)
	if err != nil {
		log.Fatalf("❌ Redis connection failed: %v", err)
	}
	defer redis.Close()

	// ─── Initialize services ─────────────────────────
	notifService := service.NewNotificationService(pg)
	auditService := service.NewAuditService(pg)
	analyticsService := service.NewAnalyticsService(pg)
	healthService := service.NewHealthService(pg, redis, cfg.SpringBootURL, cfg.PythonAIURL)

	// ─── Initialize middleware ────────────────────────
	jwtAuth := middleware.NewJWTAuth(cfg.JWTSecret, cfg.InternalSharedSecret)
	rateLimiter := middleware.NewRateLimiter(redis.Client)
	fingerprint := middleware.NewFingerprintMiddleware()

	// ─── Initialize handlers ─────────────────────────
	wsHandler := handler.NewWebSocketHandler(notifService, jwtAuth)
	notifHandler := handler.NewNotificationHandler(notifService)
	analyticsHandler := handler.NewAnalyticsHandler(analyticsService)
	auditHandler := handler.NewAuditHandler(auditService)
	healthHandler := handler.NewHealthHandler(healthService)

	// ─── Start Kafka consumers ───────────────────────
	kafkaConsumer := kafka.NewConsumer(cfg.KafkaBrokers, notifService, auditService)
	kafkaConsumer.Start(context.Background())
	defer kafkaConsumer.Stop()

	// ─── Router ──────────────────────────────────────
	r := chi.NewRouter()

	// Global middleware
	r.Use(chimw.RequestID)
	r.Use(chimw.RealIP)
	r.Use(middleware.LoggingMiddleware)
	r.Use(middleware.MetricsMiddleware)
	r.Use(chimw.Recoverer)
	r.Use(fingerprint.Middleware)
	r.Use(rateLimiter.Middleware)
	allowedOriginsStr := os.Getenv("ALLOWED_ORIGINS")
	if allowedOriginsStr == "" {
		allowedOriginsStr = "http://localhost"
	}

	r.Use(cors.Handler(cors.Options{
		AllowedOrigins:   strings.Split(allowedOriginsStr, ","),
		AllowedMethods:   []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Authorization", "Content-Type", "X-Request-ID", "X-Internal-Token"},
		ExposedHeaders:   []string{"X-Request-ID", "X-RateLimit-Remaining", "X-RateLimit-Reset", "X-Internal-Token"},
		AllowCredentials: true,
		MaxAge:           3600,
	}))

	// ─── Routes ──────────────────────────────────────

	// Health (public)
	r.Get("/health", healthHandler.GetHealth)

	// Metrics (public — Prometheus scrapes this)
	r.Handle("/metrics", promhttp.Handler())

	// WebSocket (requires token in query param)
	r.Group(func(r chi.Router) {
		r.Use(jwtAuth.OptionalAuth)
		r.Get("/ws", wsHandler.Handle)
	})

	// Notifications (authenticated)
	r.Route("/api/v1/notifications", func(r chi.Router) {
		r.Use(jwtAuth.RequireAuth)
		r.Get("/", notifHandler.GetNotifications)
		r.Get("/unread-count", notifHandler.GetUnreadCount)
		r.Put("/{id}/read", notifHandler.MarkAsRead)
		r.Put("/read-all", notifHandler.MarkAllAsRead)
	})

	// Analytics (internal or admin)
	r.Route("/api/v1/analytics", func(r chi.Router) {
		r.Use(middleware.InternalAuthMiddleware(cfg.InternalSharedSecret))
		r.Use(jwtAuth.RequireAdmin)
		r.Get("/dashboard", analyticsHandler.GetDashboard)
		r.Get("/reviews/daily", analyticsHandler.GetReviewsDaily)
		r.Get("/reviews/sentiment", analyticsHandler.GetSentimentBreakdown)
		r.Get("/colleges/popular", analyticsHandler.GetPopularColleges)
	})

	// Audit logs (internal or admin)
	r.Route("/api/v1/admin/audit", func(r chi.Router) {
		r.Use(middleware.InternalAuthMiddleware(cfg.InternalSharedSecret))
		r.Use(jwtAuth.RequireAdmin)
		r.Get("/logs", auditHandler.GetAuditLogs)
	})

	// ─── Start server ────────────────────────────────
	server := &http.Server{
		Addr:         fmt.Sprintf(":%s", cfg.Port),
		Handler:      r,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	// Graceful shutdown
	done := make(chan os.Signal, 1)
	signal.Notify(done, os.Interrupt, syscall.SIGTERM)

	go func() {
		log.Printf("✅ Edge service listening on port %s", cfg.Port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("❌ Server failed: %v", err)
		}
	}()

	<-done
	log.Println("🛑 Shutting down gracefully...")

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Fatalf("❌ Server forced shutdown: %v", err)
	}

	log.Println("✅ Edge service stopped")
}
