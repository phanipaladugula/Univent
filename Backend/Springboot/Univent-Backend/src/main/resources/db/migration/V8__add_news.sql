-- News articles table (auto-scraped)
CREATE TABLE IF NOT EXISTS news_articles (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    source_url VARCHAR(500),
    article_url VARCHAR(500) UNIQUE NOT NULL,
    published_at TIMESTAMP NOT NULL,
    college_id UUID REFERENCES colleges(id),
    summary TEXT,
    image_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    upvotes INTEGER DEFAULT 0,
    scraped_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- Student posts table
CREATE TABLE IF NOT EXISTS student_posts (
                                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    college_id UUID NOT NULL REFERENCES colleges(id),
    content VARCHAR(500) NOT NULL,
    upvotes INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    moderated_by UUID REFERENCES users(id),
    moderated_at TIMESTAMP,
    rejection_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

-- News upvotes table (track unique upvotes)
CREATE TABLE IF NOT EXISTS news_upvotes (
                                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    news_article_id UUID REFERENCES news_articles(id) ON DELETE CASCADE,
    student_post_id UUID REFERENCES student_posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(news_article_id, user_id),
    UNIQUE(student_post_id, user_id),
    CHECK (
(news_article_id IS NOT NULL AND student_post_id IS NULL) OR
(news_article_id IS NULL AND student_post_id IS NOT NULL)
    )
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_news_articles_published ON news_articles(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_news_articles_college ON news_articles(college_id);
CREATE INDEX IF NOT EXISTS idx_student_posts_college ON student_posts(college_id);
CREATE INDEX IF NOT EXISTS idx_student_posts_status ON student_posts(status);
CREATE INDEX IF NOT EXISTS idx_student_posts_created ON student_posts(created_at DESC);

-- Triggers
CREATE TRIGGER update_student_posts_updated_at
    BEFORE UPDATE ON student_posts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();