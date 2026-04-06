package com.univent.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class MaterializedViewService {

    @PersistenceContext
    private EntityManager entityManager;

    private static final String REFRESH_COLLEGE_RANKINGS = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_college_rankings";
    private static final String REFRESH_PROGRAM_LEADERBOARD = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_program_leaderboard";
    private static final String REFRESH_COLLEGE_STATS = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_college_stats";

    /**
     * Refresh all materialized views daily at 2:00 AM IST
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "Asia/Kolkata")
    @Transactional
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

    @Transactional
    public void refreshCollegeRankings() {
        log.debug("Refreshing mv_college_rankings");
        entityManager.createNativeQuery(REFRESH_COLLEGE_RANKINGS).executeUpdate();
    }

    @Transactional
    public void refreshProgramLeaderboard() {
        log.debug("Refreshing mv_program_leaderboard");
        entityManager.createNativeQuery(REFRESH_PROGRAM_LEADERBOARD).executeUpdate();
    }

    @Transactional
    public void refreshCollegeStats() {
        log.debug("Refreshing mv_college_stats");
        entityManager.createNativeQuery(REFRESH_COLLEGE_STATS).executeUpdate();
    }

    // Add this method to an existing service or create a new one
    @Scheduled(cron = "0 0 */6 * * *", zone = "Asia/Kolkata") // Every 6 hours
    @Transactional
    public void fetchNewsAutomatically() {
        log.info("Scheduled news fetch started");
         RssFeedService rssFeedService=null;
        rssFeedService.fetchAndStoreNews();
    }
}