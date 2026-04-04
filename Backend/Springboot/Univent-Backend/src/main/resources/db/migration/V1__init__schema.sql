-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Function to update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Users table
CREATE TABLE IF NOT EXISTS users (
                                     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_hash VARCHAR(255) UNIQUE NOT NULL,
    anonymous_username VARCHAR(50) UNIQUE NOT NULL,
    avatar_color VARCHAR(7) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    verified_badge BOOLEAN DEFAULT FALSE,
    reputation INTEGER DEFAULT 0,
    total_reviews INTEGER DEFAULT 0,
    current_college_id UUID,
    current_program_id UUID,
    graduation_year INTEGER,
    last_active_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Colleges table
CREATE TABLE IF NOT EXISTS colleges (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    city VARCHAR(100),
    state VARCHAR(100),
    college_type VARCHAR(50),
    is_verified BOOLEAN DEFAULT FALSE,
    website VARCHAR(255),
    email_domain VARCHAR(255),
    logo_url VARCHAR(500),
    average_rating DECIMAL(3,2) DEFAULT 0,
    total_reviews INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE TRIGGER update_colleges_updated_at
    BEFORE UPDATE ON colleges
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Programs table
CREATE TABLE IF NOT EXISTS programs (
                                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    category VARCHAR(50) NOT NULL,
    degree VARCHAR(50),
    duration_years INTEGER,
    icon VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE TRIGGER update_programs_updated_at
    BEFORE UPDATE ON programs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- College Programs junction table
CREATE TABLE IF NOT EXISTS college_programs (
                                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    college_id UUID NOT NULL REFERENCES colleges(id) ON DELETE CASCADE,
    program_id UUID NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
    fees_total DECIMAL(12,2),
    fees_per_year DECIMAL(12,2),
    seats_intake INTEGER,
    entrance_exam VARCHAR(100),
    cutoff_rank VARCHAR(50),
    median_package DECIMAL(10,2),
    highest_package DECIMAL(10,2),
    placement_percentage DECIMAL(5,2),
    average_rating DECIMAL(3,2) DEFAULT 0,
    total_reviews INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(college_id, program_id)
    );

CREATE TRIGGER update_college_programs_updated_at
    BEFORE UPDATE ON college_programs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Reviews table
CREATE TABLE IF NOT EXISTS reviews (
                                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    college_id UUID NOT NULL REFERENCES colleges(id),
    program_id UUID NOT NULL REFERENCES programs(id),
    graduation_year INTEGER NOT NULL,
    is_current_student BOOLEAN DEFAULT FALSE,
    overall_rating INTEGER NOT NULL CHECK (overall_rating BETWEEN 1 AND 5),
    teaching_quality INTEGER NOT NULL CHECK (teaching_quality BETWEEN 1 AND 5),
    placement_support INTEGER NOT NULL CHECK (placement_support BETWEEN 1 AND 5),
    infrastructure INTEGER NOT NULL CHECK (infrastructure BETWEEN 1 AND 5),
    hostel_life INTEGER NOT NULL CHECK (hostel_life BETWEEN 1 AND 5),
    campus_life INTEGER NOT NULL CHECK (campus_life BETWEEN 1 AND 5),
    value_for_money INTEGER NOT NULL CHECK (value_for_money BETWEEN 1 AND 5),
    pros TEXT[] NOT NULL,
    cons TEXT[] NOT NULL,
    review_text TEXT NOT NULL,
    would_recommend BOOLEAN NOT NULL,
    cgpa DECIMAL(4,2),
    placement_package DECIMAL(10,2),
    upvotes INTEGER DEFAULT 0,
    downvotes INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    is_verified_review BOOLEAN DEFAULT FALSE,
    sentiment VARCHAR(20),
    sentiment_score DECIMAL(5,4),
    extracted_topics TEXT[],
    is_ai_processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP
    );

-- Indexes
CREATE INDEX IF NOT EXISTS idx_reviews_college_program ON reviews(college_id, program_id);
CREATE INDEX IF NOT EXISTS idx_reviews_created_at ON reviews(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reviews_status ON reviews(status);
CREATE INDEX IF NOT EXISTS idx_reviews_overall_rating ON reviews(overall_rating DESC);
CREATE INDEX IF NOT EXISTS idx_colleges_avg_rating ON colleges(average_rating DESC);
CREATE INDEX IF NOT EXISTS idx_colleges_total_reviews ON colleges(total_reviews DESC);
CREATE INDEX IF NOT EXISTS idx_colleges_search ON colleges USING GIN(
    to_tsvector('english', COALESCE(name,'') || ' ' || COALESCE(city,'') || ' ' || COALESCE(state,''))
    );
CREATE INDEX IF NOT EXISTS idx_college_programs_lookup ON college_programs(college_id, program_id);
CREATE INDEX IF NOT EXISTS idx_users_email_hash ON users(email_hash);