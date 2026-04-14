package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"strconv"
	"time"

	"github.com/google/uuid"

	"github.com/univent/edge-service/internal/model"
	"github.com/univent/edge-service/internal/store"
)

type AuditService struct {
	pg *store.PostgresStore
}

func NewAuditService(pg *store.PostgresStore) *AuditService {
	return &AuditService{pg: pg}
}

// HandleAuditEvent processes a Kafka audit event and persists it
func (as *AuditService) HandleAuditEvent(event model.AuditEvent) {
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	var actorID *uuid.UUID
	if event.ActorID != "" {
		if uid, err := uuid.Parse(event.ActorID); err == nil {
			actorID = &uid
		}
	}

	var resourceID *uuid.UUID
	if event.ResourceID != "" {
		if rid, err := uuid.Parse(event.ResourceID); err == nil {
			resourceID = &rid
		}
	}

	metadataJSON, _ := json.Marshal(event.Metadata)

	_, err := as.pg.Pool.Exec(ctx, `
		INSERT INTO audit_logs (id, timestamp, actor_id, actor_role, actor_ip, actor_fingerprint,
		                        action, resource_type, resource_id, metadata)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
	`,
		uuid.New(), event.Timestamp, actorID, event.ActorRole, event.ActorIP,
		event.ActorFingerprint, event.Action, event.ResourceType, resourceID, metadataJSON,
	)
	if err != nil {
		log.Printf("❌ Failed to persist audit log: %v", err)
	}
}

// GetAuditLogs retrieves paginated audit logs with filters
func (as *AuditService) GetAuditLogs(ctx context.Context, action, resourceType string,
	from, to *time.Time, page, pageSize int) ([]model.AuditLog, int, error) {

	offset := (page - 1) * pageSize

	// Build query dynamically with parameterized args
	baseWhere := "WHERE 1=1"
	args := []interface{}{}
	argIdx := 1

	if action != "" {
		baseWhere += " AND action = $" + strconv.Itoa(argIdx)
		args = append(args, action)
		argIdx++
	}

	if resourceType != "" {
		baseWhere += " AND resource_type = $" + strconv.Itoa(argIdx)
		args = append(args, resourceType)
		argIdx++
	}

	if from != nil {
		baseWhere += " AND timestamp >= $" + strconv.Itoa(argIdx)
		args = append(args, *from)
		argIdx++
	}

	if to != nil {
		baseWhere += " AND timestamp <= $" + strconv.Itoa(argIdx)
		args = append(args, *to)
		argIdx++
	}

	// Count total with the SAME filters
	countQuery := "SELECT COUNT(*) FROM audit_logs " + baseWhere
	var total int
	err := as.pg.Pool.QueryRow(ctx, countQuery, args...).Scan(&total)
	if err != nil {
		return nil, 0, fmt.Errorf("count audit logs: %w", err)
	}

	// Data query with pagination
	dataQuery := `
		SELECT id, timestamp, actor_id, actor_role, actor_ip, actor_fingerprint,
		       action, resource_type, resource_id, metadata, created_at
		FROM audit_logs ` + baseWhere +
		" ORDER BY timestamp DESC LIMIT $" + strconv.Itoa(argIdx) + " OFFSET $" + strconv.Itoa(argIdx+1)
	dataArgs := append(args, pageSize, offset)

	rows, err := as.pg.Pool.Query(ctx, dataQuery, dataArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("query audit logs: %w", err)
	}
	defer rows.Close()

	var logs []model.AuditLog
	for rows.Next() {
		var al model.AuditLog
		var metadataJSON []byte
		err := rows.Scan(&al.ID, &al.Timestamp, &al.ActorID, &al.ActorRole, &al.ActorIP,
			&al.ActorFingerprint, &al.Action, &al.ResourceType, &al.ResourceID, &metadataJSON, &al.CreatedAt)
		if err != nil {
			log.Printf("⚠️ Scan audit log row: %v", err)
			continue
		}
		if metadataJSON != nil {
			json.Unmarshal(metadataJSON, &al.Metadata)
		}
		logs = append(logs, al)
	}

	return logs, total, nil
}
