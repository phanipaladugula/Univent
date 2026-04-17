-- Add missing updated_at column to reviews table
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Add ai_summary and trust_score columns as requested by the user
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS ai_summary TEXT;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS trust_score INTEGER DEFAULT 0;
