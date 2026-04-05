-- Saved comparisons table for users
CREATE TABLE IF NOT EXISTS saved_comparisons (
                                                 id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    college_ids UUID[] NOT NULL,
    program_id UUID NOT NULL REFERENCES programs(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_saved_comparisons_user ON saved_comparisons(user_id);
CREATE INDEX IF NOT EXISTS idx_saved_comparisons_program ON saved_comparisons(program_id);

-- Trigger for updated_at
CREATE TRIGGER update_saved_comparisons_updated_at
    BEFORE UPDATE ON saved_comparisons
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();