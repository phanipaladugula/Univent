package com.univent.repository;

import com.univent.model.dto.response.CollegeRankingResponse;
import com.univent.model.dto.response.CollegeStatsResponse;
import com.univent.model.dto.response.ProgramLeaderboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class RankingRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<CollegeRankingResponse> getTopCollegesByProgram(UUID programId, int limit) {
        String sql = """
            SELECT rank, college_id, college_name, college_slug, city, state, college_type,
                   program_id, program_name, category, overall_rating, review_count,
                   placement_percentage, median_package, total_fees, weighted_score, calculated_at
            FROM mv_college_rankings
            WHERE program_id = ?
            ORDER BY rank
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            List<CollegeRankingResponse> rankings = new java.util.ArrayList<>();
            while (rs.next()) {
                rankings.add(mapToCollegeRanking(rs));
            }
            return rankings;
        }, programId, limit);
    }

    public List<CollegeRankingResponse> searchCollegesByProgram(UUID programId, String query, int limit) {
        String sql = """
            SELECT rank, college_id, college_name, college_slug, city, state, college_type,
                   program_id, program_name, category, overall_rating, review_count,
                   placement_percentage, median_package, total_fees, weighted_score, calculated_at
            FROM mv_college_rankings
            WHERE program_id = ?
            AND (college_name ILIKE ? OR city ILIKE ? OR state ILIKE ?)
            ORDER BY weighted_score DESC
            LIMIT ?
        """;

        String searchPattern = "%" + query + "%";
        return jdbcTemplate.query(sql, rs -> {
            List<CollegeRankingResponse> rankings = new java.util.ArrayList<>();
            while (rs.next()) {
                rankings.add(mapToCollegeRanking(rs));
            }
            return rankings;
        }, programId, searchPattern, searchPattern, searchPattern, limit);
    }

    public List<ProgramLeaderboardResponse> getProgramLeaderboard(String category, int limit) {
        String sql = """
            SELECT rank_in_category, overall_rank, college_id, college_name, college_slug,
                   city, state, program_id, program_name, category, overall_rating, review_count,
                   placement_percentage, median_package, total_fees, weighted_score
            FROM mv_program_leaderboard
            WHERE category = ?
            ORDER BY rank_in_category
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            List<ProgramLeaderboardResponse> leaderboard = new java.util.ArrayList<>();
            while (rs.next()) {
                leaderboard.add(mapToProgramLeaderboard(rs));
            }
            return leaderboard;
        }, category, limit);
    }

    public CollegeStatsResponse getCollegeStats(UUID collegeId) {
        String sql = """
            SELECT college_id, college_name, slug, city, state, college_type,
                   total_reviews, avg_overall_rating, avg_teaching_quality, avg_infrastructure,
                   avg_hostel_life, avg_campus_life, avg_value_for_money, total_programs,
                   avg_median_package, avg_placement_percentage, calculated_at
            FROM mv_college_stats
            WHERE college_id = ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                return CollegeStatsResponse.builder()
                        .collegeId(UUID.fromString(rs.getString("college_id")))
                        .collegeName(rs.getString("college_name"))
                        .slug(rs.getString("slug"))
                        .city(rs.getString("city"))
                        .state(rs.getString("state"))
                        .collegeType(rs.getString("college_type"))
                        .totalReviews(rs.getInt("total_reviews"))
                        .avgOverallRating(rs.getBigDecimal("avg_overall_rating"))
                        .avgTeachingQuality(rs.getBigDecimal("avg_teaching_quality"))
                        .avgInfrastructure(rs.getBigDecimal("avg_infrastructure"))
                        .avgHostelLife(rs.getBigDecimal("avg_hostel_life"))
                        .avgCampusLife(rs.getBigDecimal("avg_campus_life"))
                        .avgValueForMoney(rs.getBigDecimal("avg_value_for_money"))
                        .totalPrograms(rs.getInt("total_programs"))
                        .avgMedianPackage(rs.getBigDecimal("avg_median_package"))
                        .avgPlacementPercentage(rs.getBigDecimal("avg_placement_percentage"))
                        .calculatedAt(rs.getTimestamp("calculated_at").toLocalDateTime())
                        .build();
            }
            return null;
        }, collegeId);
    }

    public List<CollegeRankingResponse> getTopCollegesOverall(int limit) {
        String sql = """
            SELECT DISTINCT ON (college_id) rank, college_id, college_name, college_slug, city, state, college_type,
                   program_id, program_name, category, overall_rating, review_count,
                   placement_percentage, median_package, total_fees, weighted_score, calculated_at
            FROM mv_college_rankings
            ORDER BY college_id, weighted_score DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, rs -> {
            List<CollegeRankingResponse> rankings = new java.util.ArrayList<>();
            while (rs.next()) {
                rankings.add(mapToCollegeRanking(rs));
            }
            return rankings;
        }, limit);
    }

    private CollegeRankingResponse mapToCollegeRanking(java.sql.ResultSet rs) throws java.sql.SQLException {
        return CollegeRankingResponse.builder()
                .rank(rs.getInt("rank"))
                .collegeId(UUID.fromString(rs.getString("college_id")))
                .collegeName(rs.getString("college_name"))
                .collegeSlug(rs.getString("college_slug"))
                .city(rs.getString("city"))
                .state(rs.getString("state"))
                .collegeType(rs.getString("college_type"))
                .programId(UUID.fromString(rs.getString("program_id")))
                .programName(rs.getString("program_name"))
                .category(rs.getString("category"))
                .overallRating(rs.getBigDecimal("overall_rating"))
                .reviewCount(rs.getInt("review_count"))
                .placementPercentage(rs.getBigDecimal("placement_percentage"))
                .medianPackage(rs.getBigDecimal("median_package"))
                .totalFees(rs.getBigDecimal("total_fees"))
                .weightedScore(rs.getBigDecimal("weighted_score"))
                .calculatedAt(rs.getTimestamp("calculated_at").toLocalDateTime())
                .build();
    }

    private ProgramLeaderboardResponse mapToProgramLeaderboard(java.sql.ResultSet rs) throws java.sql.SQLException {
        return ProgramLeaderboardResponse.builder()
                .rankInCategory(rs.getInt("rank_in_category"))
                .overallRank(rs.getInt("overall_rank"))
                .collegeId(UUID.fromString(rs.getString("college_id")))
                .collegeName(rs.getString("college_name"))
                .collegeSlug(rs.getString("college_slug"))
                .city(rs.getString("city"))
                .state(rs.getString("state"))
                .programId(UUID.fromString(rs.getString("program_id")))
                .programName(rs.getString("program_name"))
                .category(rs.getString("category"))
                .overallRating(rs.getBigDecimal("overall_rating"))
                .reviewCount(rs.getInt("review_count"))
                .placementPercentage(rs.getBigDecimal("placement_percentage"))
                .medianPackage(rs.getBigDecimal("median_package"))
                .totalFees(rs.getBigDecimal("total_fees"))
                .weightedScore(rs.getBigDecimal("weighted_score"))
                .build();
    }
}