-- Materialized views need a unique index for CONCURRENT refresh.
CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_college_rankings_college_program
    ON mv_college_rankings(college_id, program_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_program_leaderboard_category_rank_college_program
    ON mv_program_leaderboard(category, rank_in_category, college_id, program_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_college_stats_college
    ON mv_college_stats(college_id);
