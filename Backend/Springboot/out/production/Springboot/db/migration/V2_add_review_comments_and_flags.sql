-- Review comments table
CREATE TABLE IF NOT EXISTS review_comments (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    parent_comment_id UUID REFERENCES review_comments(id) ON DELETE CASCADE,
    comment_text TEXT NOT NULL,
    upvotes INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Flagged content table
CREATE TABLE IF NOT EXISTS flagged_content (
                                               id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    review_id UUID REFERENCES reviews(id) ON DELETE CASCADE,
    comment_id UUID REFERENCES review_comments(id) ON DELETE CASCADE,
    flagged_by UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolved_by UUID REFERENCES users(id),
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_review_or_comment CHECK (
(review_id IS NOT NULL AND comment_id IS NULL) OR
(review_id IS NULL AND comment_id IS NOT NULL)
    )
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_review_comments_review ON review_comments(review_id);
CREATE INDEX IF NOT EXISTS idx_review_comments_parent ON review_comments(parent_comment_id);
CREATE INDEX IF NOT EXISTS idx_flagged_content_status ON flagged_content(status);
CREATE INDEX IF NOT EXISTS idx_flagged_content_review ON flagged_content(review_id);