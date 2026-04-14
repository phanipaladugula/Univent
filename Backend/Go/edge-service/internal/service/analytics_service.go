package service

import (
	"context"
	"strconv"
	"time"

	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/store"
)

type AnalyticsService struct {
	pg *store.PostgresStore
}

func NewAnalyticsService(pg *store.PostgresStore) *AnalyticsService {
	return &AnalyticsService{pg: pg}
}

// GetDashboard returns the admin dashboard overview
func (as *AnalyticsService) GetDashboard(ctx context.Context) (*model.DashboardResponse, error) {
	metrics, err := as.getDashboardMetrics(ctx)
	if err != nil {
		return nil, err
	}

	trends, err := as.getDashboardTrends(ctx)
	if err != nil {
		return nil, err
	}

	return &model.DashboardResponse{
		Period:  "today",
		Metrics: *metrics,
		Trends:  *trends,
	}, nil
}

func (as *AnalyticsService) getDashboardMetrics(ctx context.Context) (*model.DashboardMetrics, error) {
	m := &model.DashboardMetrics{}
	today := time.Now().Format("2006-01-02")

	// Total users
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM users`).Scan(&m.TotalUsers)

	// Active users today
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM users WHERE last_active_at::date = $1`, today).Scan(&m.ActiveUsersToday)

	// Reviews today
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM reviews WHERE created_at::date = $1`, today).Scan(&m.ReviewsToday)

	// Pending reviews
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM reviews WHERE status = 'PENDING'`).Scan(&m.ReviewsPending)

	// Avg sentiment
	as.pg.Pool.QueryRow(ctx, `SELECT COALESCE(AVG(sentiment_score), 0) FROM reviews WHERE sentiment_score IS NOT NULL`).Scan(&m.AvgReviewSentiment)

	// Pending verifications
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM users WHERE verification_status = 'PENDING'`).Scan(&m.VerificationsPending)

	// Pending flags
	as.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM flagged_content WHERE status = 'PENDING'`).Scan(&m.FlagsPending)

	return m, nil
}

func (as *AnalyticsService) getDashboardTrends(ctx context.Context) (*model.DashboardTrends, error) {
	trends := &model.DashboardTrends{
		Reviews7D: make([]int, 7),
		Users7D:   make([]int, 7),
	}

	// Reviews per day for last 7 days
	rows, err := as.pg.Pool.Query(ctx, `
		SELECT created_at::date AS day, COUNT(*) AS cnt
		FROM reviews
		WHERE created_at >= NOW() - INTERVAL '7 days'
		GROUP BY day
		ORDER BY day
	`)
	if err == nil {
		dayMap := make(map[string]int)
		for rows.Next() {
			var day time.Time
			var cnt int
			rows.Scan(&day, &cnt)
			dayMap[day.Format("2006-01-02")] = cnt
		}
		rows.Close()

		for i := 6; i >= 0; i-- {
			d := time.Now().AddDate(0, 0, -i).Format("2006-01-02")
			trends.Reviews7D[6-i] = dayMap[d]
		}
	}

	// Active users per day for last 7 days
	rows2, err := as.pg.Pool.Query(ctx, `
		SELECT last_active_at::date AS day, COUNT(*) AS cnt
		FROM users
		WHERE last_active_at >= NOW() - INTERVAL '7 days'
		GROUP BY day
		ORDER BY day
	`)
	if err == nil {
		dayMap := make(map[string]int)
		for rows2.Next() {
			var day time.Time
			var cnt int
			rows2.Scan(&day, &cnt)
			dayMap[day.Format("2006-01-02")] = cnt
		}
		rows2.Close()

		for i := 6; i >= 0; i-- {
			d := time.Now().AddDate(0, 0, -i).Format("2006-01-02")
			trends.Users7D[6-i] = dayMap[d]
		}
	}

	return trends, nil
}

// GetReviewsDaily returns review counts per day
func (as *AnalyticsService) GetReviewsDaily(ctx context.Context, days int) ([]map[string]interface{}, error) {
	rows, err := as.pg.Pool.Query(ctx, `
		SELECT created_at::date AS day, 
		       COUNT(*) AS total,
		       COUNT(CASE WHEN status = 'PUBLISHED' THEN 1 END) AS published,
		       COUNT(CASE WHEN status = 'PENDING' THEN 1 END) AS pending,
		       COUNT(CASE WHEN status = 'REMOVED' THEN 1 END) AS removed
		FROM reviews
		WHERE created_at >= NOW() - ($1 || ' days')::INTERVAL
		GROUP BY day
		ORDER BY day
	`, strconv.Itoa(days))
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []map[string]interface{}
	for rows.Next() {
		var day time.Time
		var total, published, pending, removed int
		rows.Scan(&day, &total, &published, &pending, &removed)
		results = append(results, map[string]interface{}{
			"date":      day.Format("2006-01-02"),
			"total":     total,
			"published": published,
			"pending":   pending,
			"removed":   removed,
		})
	}

	return results, nil
}

// GetPopularColleges returns most reviewed/searched colleges
func (as *AnalyticsService) GetPopularColleges(ctx context.Context, limit int) ([]map[string]interface{}, error) {
	rows, err := as.pg.Pool.Query(ctx, `
		SELECT c.id, c.name, c.slug, c.city, c.state,
		       c.total_reviews, c.average_rating
		FROM colleges c
		ORDER BY c.total_reviews DESC, c.average_rating DESC
		LIMIT $1
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var results []map[string]interface{}
	for rows.Next() {
		var id, name, slug, city, state string
		var totalReviews int
		var avgRating float64
		rows.Scan(&id, &name, &slug, &city, &state, &totalReviews, &avgRating)
		results = append(results, map[string]interface{}{
			"id":             id,
			"name":           name,
			"slug":           slug,
			"city":           city,
			"state":          state,
			"total_reviews":  totalReviews,
			"average_rating": avgRating,
		})
	}

	return results, nil
}

// GetSentimentBreakdown returns sentiment distribution
func (as *AnalyticsService) GetSentimentBreakdown(ctx context.Context) (map[string]int, error) {
	result := map[string]int{
		"POSITIVE": 0,
		"NEUTRAL":  0,
		"NEGATIVE": 0,
	}

	rows, err := as.pg.Pool.Query(ctx, `
		SELECT COALESCE(sentiment, 'UNKNOWN'), COUNT(*)
		FROM reviews
		WHERE status = 'PUBLISHED' AND sentiment IS NOT NULL
		GROUP BY sentiment
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var sentiment string
		var count int
		rows.Scan(&sentiment, &count)
		result[sentiment] = count
	}

	return result, nil
}
