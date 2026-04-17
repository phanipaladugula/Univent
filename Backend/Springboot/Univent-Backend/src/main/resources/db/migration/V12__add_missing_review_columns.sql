-- Add missing updated_at column to reviews table
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Add ai_summary and trust_score columns as requested by the user
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS ai_summary TEXT;
ALTER TABLE reviews ADD COLUMN IF NOT EXISTS trust_score INTEGER DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger WHERE tgname = 'update_reviews_updated_at'
    ) THEN
        CREATE TRIGGER update_reviews_updated_at
            BEFORE UPDATE ON reviews
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
