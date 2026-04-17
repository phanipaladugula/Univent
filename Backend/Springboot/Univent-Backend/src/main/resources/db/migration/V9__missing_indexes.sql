-- V9: Performance Optimization - Missing Indexes
-- ==============================================

-- Add individual index for program_id in reviews (V1 only had a composite index)
CREATE INDEX IF NOT EXISTS idx_reviews_program_id ON reviews(program_id);

-- Add individual index for program_id in college_programs (V1 only had a composite index)
CREATE INDEX IF NOT EXISTS idx_college_programs_program_id ON college_programs(program_id);

-- Add indexes for user-college-program lookups (frequent for personalizing feeds/chat)
CREATE INDEX IF NOT EXISTS idx_users_current_college ON users(current_college_id);
CREATE INDEX IF NOT EXISTS idx_users_current_program ON users(current_program_id);

-- Add index for audit logs metadata searches (for analytics scaling)
CREATE INDEX IF NOT EXISTS idx_audit_logs_metadata_gin ON audit_logs USING GIN(metadata);

-- Add index for notification read status lookups (performance in notification tray)
-- (Existing idx_notifications_user covers common cases, but this aids broad status queries)
CREATE INDEX IF NOT EXISTS idx_notifications_status_unread ON notifications(user_id) WHERE is_read = FALSE;
