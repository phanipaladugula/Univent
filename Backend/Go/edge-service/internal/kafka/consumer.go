package kafka

import (
	"context"
	"encoding/json"
	"log"
	"time"

	kafkago "github.com/segmentio/kafka-go"

	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/service"
)

const (
	TopicNotificationOutbound = "notification.outbound"
	TopicAuditEvents          = "audit.events"
	TopicReviewProcessed      = "review.processed"
)

type Consumer struct {
	brokers        []string
	notifService   *service.NotificationService
	auditService   *service.AuditService
	cancel         context.CancelFunc
}

func NewConsumer(brokers []string, notifService *service.NotificationService, auditService *service.AuditService) *Consumer {
	return &Consumer{
		brokers:      brokers,
		notifService: notifService,
		auditService: auditService,
	}
}

func (c *Consumer) Start(ctx context.Context) {
	ctx, cancel := context.WithCancel(ctx)
	c.cancel = cancel

	go c.consumeNotifications(ctx)
	go c.consumeAuditEvents(ctx)
	go c.consumeReviewProcessed(ctx)

	log.Println("📡 Kafka consumers started")
}

func (c *Consumer) Stop() {
	if c.cancel != nil {
		c.cancel()
	}
}

func (c *Consumer) consumeNotifications(ctx context.Context) {
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:        c.brokers,
		Topic:          TopicNotificationOutbound,
		GroupID:         "go-edge-notifications",
		MinBytes:       1,
		MaxBytes:       10e6,
		MaxWait:        500 * time.Millisecond,
		CommitInterval: time.Second,
		StartOffset:    kafkago.LastOffset,
	})
	defer reader.Close()

	log.Printf("📡 Consuming from %s", TopicNotificationOutbound)

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("⚠️ Error reading notification: %v", err)
			time.Sleep(time.Second)
			continue
		}

		var event model.NotificationEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			log.Printf("⚠️ Error parsing notification event: %v", err)
			continue
		}

		c.notifService.HandleNotificationEvent(event)
	}
}

func (c *Consumer) consumeAuditEvents(ctx context.Context) {
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:        c.brokers,
		Topic:          TopicAuditEvents,
		GroupID:         "go-edge-audit",
		MinBytes:       1,
		MaxBytes:       10e6,
		MaxWait:        500 * time.Millisecond,
		CommitInterval: time.Second,
		StartOffset:    kafkago.LastOffset,
	})
	defer reader.Close()

	log.Printf("📡 Consuming from %s", TopicAuditEvents)

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("⚠️ Error reading audit event: %v", err)
			time.Sleep(time.Second)
			continue
		}

		var event model.AuditEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			log.Printf("⚠️ Error parsing audit event: %v", err)
			continue
		}

		c.auditService.HandleAuditEvent(event)
	}
}

func (c *Consumer) consumeReviewProcessed(ctx context.Context) {
	reader := kafkago.NewReader(kafkago.ReaderConfig{
		Brokers:        c.brokers,
		Topic:          TopicReviewProcessed,
		GroupID:        "go-edge-reviews",
		MinBytes:       1,
		MaxBytes:       10e6,
		MaxWait:        500 * time.Millisecond,
		CommitInterval: time.Second,
		StartOffset:    kafkago.LastOffset,
	})
	defer reader.Close()

	log.Printf("📡 Consuming from %s", TopicReviewProcessed)

	for {
		select {
		case <-ctx.Done():
			return
		default:
		}

		msg, err := reader.ReadMessage(ctx)
		if err != nil {
			if ctx.Err() != nil {
				return
			}
			log.Printf("⚠️ Error reading review processed event: %v", err)
			time.Sleep(time.Second)
			continue
		}

		// Acknowledge consuming the message so that we know it works, maybe print a log
		log.Printf("✅ Edge Service received review.processed event from Python AI Worker: %s", string(msg.Value))
		// The edge service doesn't actually process review.processed to store it (Spring Boot does), 
		// but it CAN send a real-time raw WebSocket event if required.
		// As per task instructions just ensure `consumeReviewProcessed` goroutine exists.
	}
}
