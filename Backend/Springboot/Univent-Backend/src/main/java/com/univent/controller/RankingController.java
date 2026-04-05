package com.univent.controller;

import com.univent.model.dto.response.CollegeRankingResponse;
import com.univent.model.dto.response.CollegeStatsResponse;
import com.univent.model.dto.response.ProgramLeaderboardResponse;
import com.univent.repository.RankingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
@Slf4j
public class RankingController {

    private final RankingRepository rankingRepository;

    @GetMapping("/program/{programId}")
    public ResponseEntity<List<CollegeRankingResponse>> getTopCollegesByProgram(
            @PathVariable UUID programId,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching top {} colleges for program: {}", limit, programId);
        return ResponseEntity.ok(rankingRepository.getTopCollegesByProgram(programId, limit));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CollegeRankingResponse>> searchColleges(
            @RequestParam UUID programId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Searching colleges for program: {} with query: {}", programId, query);
        return ResponseEntity.ok(rankingRepository.searchCollegesByProgram(programId, query, limit));
    }

    @GetMapping("/leaderboard/{category}")
    public ResponseEntity<List<ProgramLeaderboardResponse>> getProgramLeaderboard(
            @PathVariable String category,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching leaderboard for category: {} with limit: {}", category, limit);
        return ResponseEntity.ok(rankingRepository.getProgramLeaderboard(category, limit));
    }

    @GetMapping("/college/{collegeId}/stats")
    public ResponseEntity<CollegeStatsResponse> getCollegeStats(@PathVariable UUID collegeId) {
        log.info("Fetching stats for college: {}", collegeId);
        CollegeStatsResponse stats = rankingRepository.getCollegeStats(collegeId);
        if (stats == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/top")
    public ResponseEntity<List<CollegeRankingResponse>> getTopCollegesOverall(
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Fetching top {} colleges overall", limit);
        return ResponseEntity.ok(rankingRepository.getTopCollegesOverall(limit));
    }
}