package com.univent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.annotation.Backoff;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class MaterializedViewService {

    private final RssFeedService rssFeedService;
    private final DataSource dataSource;

    private static final String REFRESH_COLLEGE_RANKINGS = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_college_rankings";
    private static final String REFRESH_PROGRAM_LEADERBOARD = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_program_leaderboard";
    private static final String REFRESH_COLLEGE_STATS = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_college_stats";

    /**
     * Refresh all materialized views daily at 2:00 AM IST
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    public void refreshAllMaterializedViews() {
        log.info("Starting refresh of all materialized views");
        long startTime = System.currentTimeMillis();

        try {
            refreshCollegeRankings();
            refreshProgramLeaderboard();
            refreshCollegeStats();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully refreshed all materialized views in {} ms", duration);
        } catch (Exception e) {
            log.error("Failed to refresh materialized views: {}", e.getMessage(), e);
        }
    }

    public void refreshCollegeRankings() {
        log.debug("Refreshing mv_college_rankings");
        executeNativeQuery(REFRESH_COLLEGE_RANKINGS);
    }

    public void refreshProgramLeaderboard() {
        log.debug("Refreshing mv_program_leaderboard");
        executeNativeQuery(REFRESH_PROGRAM_LEADERBOARD);
    }

    public void refreshCollegeStats() {
        log.debug("Refreshing mv_college_stats");
        executeNativeQuery(REFRESH_COLLEGE_STATS);
    }

    private void executeNativeQuery(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            log.error("Error executing native query: {}", sql, e);
            throw new RuntimeException("Materialized view refresh failed", e);
        }
    }

    @Scheduled(cron = "0 0 */6 * * *", zone = "Asia/Kolkata") // Every 6 hours
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 60000))
    public void fetchNewsAutomatically() {
        log.info("Scheduled news fetch started");
        rssFeedService.fetchAndStoreNews();
    }
}
