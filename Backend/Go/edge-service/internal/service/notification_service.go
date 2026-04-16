package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	"github.com/google/uuid"
	"github.com/gorilla/websocket"

	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/store"
)

var wsMissedDeliveriesTotal = promauto.NewCounter(prometheus.CounterOpts{
	Name: "ws_missed_deliveries_total",
	Help: "Total number of websocket notification deliveries missed due to full buffer",
})

// NotificationService manages WebSocket connections and routes notifications
type NotificationService struct {
	connections map[uuid.UUID]*clientConn
	mu          sync.RWMutex
	pg          *store.PostgresStore
}

type clientConn struct {
	conn   *websocket.Conn
	userID uuid.UUID
	send   chan []byte
}

func NewNotificationService(pg *store.PostgresStore) *NotificationService {
	return &NotificationService{
		connections: make(map[uuid.UUID]*clientConn),
		pg:          pg,
	}
}

// RegisterClient adds a WebSocket connection for a user
func (ns *NotificationService) RegisterClient(userID uuid.UUID, conn *websocket.Conn) {
	ns.mu.Lock()
	defer ns.mu.Unlock()

	// Close existing connection if any
	if existing, ok := ns.connections[userID]; ok {
		close(existing.send)
		existing.conn.Close()
	}

	client := &clientConn{
		conn:   conn,
		userID: userID,
		send:   make(chan []byte, 64),
	}
	ns.connections[userID] = client

	// Start write pump
	go ns.writePump(client)

	log.Printf("🔌 WebSocket connected: user=%s (total: %d)", userID, len(ns.connections))
}

// UnregisterClient removes a WebSocket connection
func (ns *NotificationService) UnregisterClient(userID uuid.UUID) {
	ns.mu.Lock()
	defer ns.mu.Unlock()

	if client, ok := ns.connections[userID]; ok {
		close(client.send)
		client.conn.Close()
		delete(ns.connections, userID)
		log.Printf("🔌 WebSocket disconnected: user=%s (total: %d)", userID, len(ns.connections))
	}
}

// SendToUser sends a notification to a specific user
func (ns *NotificationService) SendToUser(userID uuid.UUID, notification model.Notification) error {
	// Always persist to database
	err := ns.persistNotification(notification)
	if err != nil {
		log.Printf("⚠️ Failed to persist notification: %v", err)
	}

	// Try to send via WebSocket
	ns.mu.RLock()
	client, online := ns.connections[userID]
	ns.mu.RUnlock()

	if online {
		data, err := json.Marshal(model.WSMessage{
			Type: notification.Type,
			Data: notification,
		})
		if err != nil {
			return fmt.Errorf("marshal notification: %w", err)
		}

		select {
		case client.send <- data:
			return nil
		default:
			// Channel full, user will see it when they poll
			wsMissedDeliveriesTotal.Inc()
			log.Printf("⚠️ Send buffer full for user %s, notification persisted for later", userID)
			return nil
		}
	}

	return nil // Not online, but persisted
}

// HandleNotificationEvent processes a Kafka notification event
func (ns *NotificationService) HandleNotificationEvent(event model.NotificationEvent) {
	notification := model.Notification{
		ID:        uuid.New(),
		UserID:    event.UserID,
		Type:      event.Type,
		Title:     event.Title,
		Body:      event.Body,
		Data:      event.Data,
		IsRead:    false,
		CreatedAt: event.Timestamp,
	}

	if err := ns.SendToUser(event.UserID, notification); err != nil {
		log.Printf("❌ Failed to send notification to %s: %v", event.UserID, err)
	}
}

// GetUserNotifications fetches paginated notifications from DB
func (ns *NotificationService) GetUserNotifications(ctx context.Context, userID uuid.UUID, page, pageSize int) ([]model.Notification, int, error) {
	offset := (page - 1) * pageSize

	rows, err := ns.pg.Pool.Query(ctx, `
		SELECT id, user_id, type, title, body, data, is_read, created_at
		FROM notifications
		WHERE user_id = $1
		ORDER BY created_at DESC
		LIMIT $2 OFFSET $3
	`, userID, pageSize, offset)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	var notifications []model.Notification
	for rows.Next() {
		var n model.Notification
		var dataJSON []byte
		err := rows.Scan(&n.ID, &n.UserID, &n.Type, &n.Title, &n.Body, &dataJSON, &n.IsRead, &n.CreatedAt)
		if err != nil {
			continue
		}
		if dataJSON != nil {
			json.Unmarshal(dataJSON, &n.Data)
		}
		notifications = append(notifications, n)
	}

	// Count total
	var total int
	ns.pg.Pool.QueryRow(ctx, `SELECT COUNT(*) FROM notifications WHERE user_id = $1`, userID).Scan(&total)

	return notifications, total, nil
}

// MarkAsRead marks a notification as read
func (ns *NotificationService) MarkAsRead(ctx context.Context, userID, notificationID uuid.UUID) error {
	_, err := ns.pg.Pool.Exec(ctx,
		`UPDATE notifications SET is_read = TRUE WHERE id = $1 AND user_id = $2`,
		notificationID, userID)
	return err
}

// MarkAllAsRead marks all notifications as read for a user
func (ns *NotificationService) MarkAllAsRead(ctx context.Context, userID uuid.UUID) error {
	_, err := ns.pg.Pool.Exec(ctx,
		`UPDATE notifications SET is_read = TRUE WHERE user_id = $1 AND is_read = FALSE`,
		userID)
	return err
}

// GetUnreadCount returns the number of unread notifications
func (ns *NotificationService) GetUnreadCount(ctx context.Context, userID uuid.UUID) (int, error) {
	var count int
	err := ns.pg.Pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM notifications WHERE user_id = $1 AND is_read = FALSE`,
		userID).Scan(&count)
	return count, err
}

// ConnectionCount returns number of active WebSocket connections
func (ns *NotificationService) ConnectionCount() int {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	return len(ns.connections)
}

func (ns *NotificationService) persistNotification(n model.Notification) error {
	dataJSON, _ := json.Marshal(n.Data)
	_, err := ns.pg.Pool.Exec(context.Background(), `
		INSERT INTO notifications (id, user_id, type, title, body, data, is_read, created_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
	`, n.ID, n.UserID, n.Type, n.Title, n.Body, dataJSON, n.IsRead, n.CreatedAt)
	return err
}

func (ns *NotificationService) writePump(client *clientConn) {
	ticker := time.NewTicker(54 * time.Second)
	defer func() {
		ticker.Stop()
		client.conn.Close()
	}()

	for {
		select {
		case msg, ok := <-client.send:
			if !ok {
				client.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}
			if err := client.conn.WriteMessage(websocket.TextMessage, msg); err != nil {
				log.Printf("⚠️ WebSocket write error for user %s: %v", client.userID, err)
				ns.UnregisterClient(client.userID)
				return
			}
		case <-ticker.C:
			if err := client.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				log.Printf("⚠️ WebSocket ping error for user %s: %v", client.userID, err)
				ns.UnregisterClient(client.userID)
				return
			}
		}
	}
}
