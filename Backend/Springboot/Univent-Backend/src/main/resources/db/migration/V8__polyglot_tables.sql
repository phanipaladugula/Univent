-- ==============================================
-- V9: Polyglot Architecture Supporting Tables
-- ==============================================

-- Device fingerprints (Go edge service)
CREATE TABLE IF NOT EXISTS device_fingerprints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fingerprint_hash VARCHAR(64) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    user_agent TEXT,
    ip_address INET,
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    request_count INTEGER DEFAULT 1,
    is_flagged BOOLEAN DEFAULT FALSE,
    UNIQUE(fingerprint_hash, user_id)
);

-- Audit logs (Go edge service writes, admin reads)
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id UUID,
    actor_role VARCHAR(20),
    actor_ip VARCHAR(45),
    actor_fingerprint VARCHAR(64),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(30) NOT NULL,
    resource_id UUID,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Analytics daily snapshots (Go edge service)
CREATE TABLE IF NOT EXISTS analytics_daily (
    date DATE NOT NULL PRIMARY KEY,
    total_users INTEGER DEFAULT 0,
    new_users INTEGER DEFAULT 0,
    active_users INTEGER DEFAULT 0,
    reviews_submitted INTEGER DEFAULT 0,
    reviews_published INTEGER DEFAULT 0,
    reviews_rejected INTEGER DEFAULT 0,
    comparisons_made INTEGER DEFAULT 0,
    ai_chat_requests INTEGER DEFAULT 0,
    verifications_submitted INTEGER DEFAULT 0,
    verifications_approved INTEGER DEFAULT 0,
    avg_sentiment_score DECIMAL(5,4),
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notification preferences (per user)
CREATE TABLE IF NOT EXISTS notification_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    review_approved BOOLEAN DEFAULT TRUE,
    review_rejected BOOLEAN DEFAULT TRUE,
    new_comment BOOLEAN DEFAULT TRUE,
    verification_update BOOLEAN DEFAULT TRUE,
    post_update BOOLEAN DEFAULT TRUE,
    milestone_upvote BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notification history
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    data JSONB,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AI chat sessions (Python AI service)
CREATE TABLE IF NOT EXISTS ai_chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AI chat messages
CREATE TABLE IF NOT EXISTS ai_chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('user', 'assistant')),
    content TEXT NOT NULL,
    citations JSONB,
    tools_used JSONB,
    processing_time_ms INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(user_id) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_device_fp_hash ON device_fingerprints(fingerprint_hash);
CREATE INDEX IF NOT EXISTS idx_device_fp_user ON device_fingerprints(user_id);
CREATE INDEX IF NOT EXISTS idx_device_fp_flagged ON device_fingerprints(is_flagged) WHERE is_flagged = TRUE;
CREATE INDEX IF NOT EXISTS idx_ai_chat_user ON ai_chat_sessions(user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_chat_messages_session ON ai_chat_messages(session_id, created_at);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'update_notification_prefs_updated_at'
    ) THEN
        CREATE TRIGGER update_notification_prefs_updated_at
            BEFORE UPDATE ON notification_preferences
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'update_ai_chat_sessions_updated_at'
    ) THEN
        CREATE TRIGGER update_ai_chat_sessions_updated_at
            BEFORE UPDATE ON ai_chat_sessions
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
