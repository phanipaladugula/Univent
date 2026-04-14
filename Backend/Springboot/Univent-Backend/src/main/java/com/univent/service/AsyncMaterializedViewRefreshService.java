package com.univent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncMaterializedViewRefreshService {

    private final MaterializedViewService materializedViewService;

    @Async
    public void refreshMaterializedViewsAsync() {
        log.info("Async refresh of materialized views started");
        long startTime = System.currentTimeMillis();

        try {
            materializedViewService.refreshCollegeRankings();
            materializedViewService.refreshProgramLeaderboard();
            materializedViewService.refreshCollegeStats();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Async refresh completed in {} ms", duration);
        } catch (Exception e) {
            log.error("Async refresh failed: {}", e.getMessage(), e);
        }
    }
}