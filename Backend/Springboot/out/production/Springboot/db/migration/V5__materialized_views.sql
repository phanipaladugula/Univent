-- ============================================
-- HELPER FUNCTION (Must be first)
-- ============================================

CREATE OR REPLACE FUNCTION calculate_roi_score(median_package NUMERIC, fees_total NUMERIC, duration_years INTEGER)
RETURNS NUMERIC AS $$
BEGIN
    IF median_package IS NULL OR fees_total IS NULL OR duration_years IS NULL THEN
        RETURN 0;
END IF;
RETURN GREATEST(0, (median_package * 4 - fees_total) / duration_years);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- ============================================
-- MATERIALIZED VIEW 1: College Rankings
-- ============================================

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_college_rankings AS
WITH review_stats AS (
    SELECT
        r.college_id,
        r.program_id,
        AVG(r.overall_rating) as avg_rating,
        COUNT(r.id) as review_count,
        AVG(r.teaching_quality) as avg_teaching,
        AVG(r.infrastructure) as avg_infra,
        AVG(r.hostel_life) as avg_hostel,
        AVG(r.campus_life) as avg_campus,
        AVG(r.value_for_money) as avg_value,
        (COUNT(CASE WHEN r.would_recommend = true THEN 1 END) * 100.0 / NULLIF(COUNT(r.id), 0)) as recommend_percent
    FROM reviews r
    WHERE r.status = 'PUBLISHED'
    GROUP BY r.college_id, r.program_id
),
placement_stats AS (
    SELECT
        cp.college_id,
        cp.program_id,
        cp.median_package,
        cp.fees_total,
        cp.placement_percentage,
        p.duration_years
    FROM college_programs cp
    JOIN programs p ON p.id = cp.program_id
)
SELECT
    ROW_NUMBER() OVER (ORDER BY
        (COALESCE(rs.avg_rating, 0) * 0.35 +
         COALESCE(ps.placement_percentage, 0) * 0.30 +
         COALESCE(calculate_roi_score(ps.median_package, ps.fees_total, ps.duration_years), 0) * 0.20 +
         COALESCE(rs.avg_infra, 0) * 0.10 +
         COALESCE(rs.avg_teaching, 0) * 0.05) DESC
    ) as rank,
    c.id as college_id,
    c.name as college_name,
    c.slug as college_slug,
    c.city,
    c.state,
    c.college_type,
    p.id as program_id,
    p.name as program_name,
    p.category,
    COALESCE(rs.avg_rating, 0) as overall_rating,
    COALESCE(rs.review_count, 0) as review_count,
    COALESCE(ps.placement_percentage, 0) as placement_percentage,
    COALESCE(ps.median_package, 0) as median_package,
    COALESCE(ps.fees_total, 0) as total_fees,
    COALESCE(rs.avg_teaching, 0) as teaching_quality,
    COALESCE(rs.avg_infra, 0) as infrastructure,
    COALESCE(rs.avg_hostel, 0) as hostel_life,
    COALESCE(rs.avg_campus, 0) as campus_life,
    COALESCE(rs.avg_value, 0) as value_for_money,
    COALESCE(rs.recommend_percent, 0) as would_recommend_percent,
    COALESCE(calculate_roi_score(ps.median_package, ps.fees_total, ps.duration_years), 0) as roi_score,
    (COALESCE(rs.avg_rating, 0) * 0.35 +
     COALESCE(ps.placement_percentage, 0) * 0.30 +
     COALESCE(calculate_roi_score(ps.median_package, ps.fees_total, ps.duration_years), 0) * 0.20 +
     COALESCE(rs.avg_infra, 0) * 0.10 +
     COALESCE(rs.avg_teaching, 0) * 0.05) as weighted_score,
    NOW() as calculated_at
FROM colleges c
         CROSS JOIN programs p
         LEFT JOIN review_stats rs ON rs.college_id = c.id AND rs.program_id = p.id
         LEFT JOIN placement_stats ps ON ps.college_id = c.id AND ps.program_id = p.id
WHERE EXISTS (SELECT 1 FROM college_programs cp WHERE cp.college_id = c.id AND cp.program_id = p.id);

-- ============================================
-- MATERIALIZED VIEW 2: Program Leaderboard
-- ============================================

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_program_leaderboard AS
SELECT
    ROW_NUMBER() OVER (PARTITION BY p.category ORDER BY mvw.weighted_score DESC) as rank_in_category,
    mvw.rank as overall_rank,
    mvw.college_id,
    mvw.college_name,
    mvw.college_slug,
    mvw.city,
    mvw.state,
    mvw.program_id,
    mvw.program_name,
    p.category,
    mvw.overall_rating,
    mvw.review_count,
    mvw.placement_percentage,
    mvw.median_package,
    mvw.total_fees,
    mvw.weighted_score
FROM mv_college_rankings mvw
         JOIN programs p ON p.id = mvw.program_id
WHERE mvw.review_count > 0 OR mvw.placement_percentage > 0;

-- ============================================
-- MATERIALIZED VIEW 3: College Stats
-- ============================================

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_college_stats AS
SELECT
    c.id as college_id,
    c.name as college_name,
    c.slug,
    c.city,
    c.state,
    c.college_type,
    COUNT(DISTINCT r.id) as total_reviews,
    COALESCE(AVG(r.overall_rating), 0) as avg_overall_rating,
    COALESCE(AVG(r.teaching_quality), 0) as avg_teaching_quality,
    COALESCE(AVG(r.infrastructure), 0) as avg_infrastructure,
    COALESCE(AVG(r.hostel_life), 0) as avg_hostel_life,
    COALESCE(AVG(r.campus_life), 0) as avg_campus_life,
    COALESCE(AVG(r.value_for_money), 0) as avg_value_for_money,
    COUNT(DISTINCT cp.program_id) as total_programs,
    COALESCE(AVG(cp.median_package), 0) as avg_median_package,
    COALESCE(AVG(cp.placement_percentage), 0) as avg_placement_percentage,
    NOW() as calculated_at
FROM colleges c
         LEFT JOIN reviews r ON r.college_id = c.id AND r.status = 'PUBLISHED'
         LEFT JOIN college_programs cp ON cp.college_id = c.id
GROUP BY c.id, c.name, c.slug, c.city, c.state, c.college_type;

-- ============================================
-- INDEXES
-- ============================================

CREATE INDEX IF NOT EXISTS idx_mv_college_rankings_college ON mv_college_rankings(college_id);
CREATE INDEX IF NOT EXISTS idx_mv_college_rankings_program ON mv_college_rankings(program_id);
CREATE INDEX IF NOT EXISTS idx_mv_college_rankings_rank ON mv_college_rankings(rank);
CREATE INDEX IF NOT EXISTS idx_mv_college_rankings_weighted ON mv_college_rankings(weighted_score DESC);

CREATE INDEX IF NOT EXISTS idx_mv_program_leaderboard_category ON mv_program_leaderboard(category);
CREATE INDEX IF NOT EXISTS idx_mv_program_leaderboard_rank ON mv_program_leaderboard(rank_in_category);

CREATE INDEX IF NOT EXISTS idx_mv_college_stats_college ON mv_college_stats(college_id);
CREATE INDEX IF NOT EXISTS idx_mv_college_stats_state ON mv_college_stats(state);
CREATE INDEX IF NOT EXISTS idx_mv_college_stats_rating ON mv_college_stats(avg_overall_rating DESC);