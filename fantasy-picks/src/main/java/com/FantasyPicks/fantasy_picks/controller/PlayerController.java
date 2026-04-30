package com.FantasyPicks.fantasy_picks.controller;

import com.FantasyPicks.fantasy_picks.model.PlayerApiResponse;
import com.FantasyPicks.fantasy_picks.service.PlayerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for player rankings.
 * Serves the data that the HTML frontend fetches.
 */
@RestController
@RequestMapping("/api")
public class PlayerController {

    private static final Logger log = LoggerFactory.getLogger(PlayerController.class);

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /**
     * GET /api/players?sources=fantasypros,espn,...&year=2026
     *
     * Fetches player rankings from the specified sources for the given season year,
     * merges them, and returns the combined data with overall consensus rankings.
     *
     * @param sources Comma-separated list of source IDs to scrape from
     * @param year    The season year to fetch rankings for (defaults to current year)
     * @return PlayerApiResponse containing merged player data
     */
    @GetMapping("/players")
    public ResponseEntity<PlayerApiResponse> getPlayers(
            @RequestParam(name = "sources", defaultValue = "fantasypros") String sources,
            @RequestParam(name = "year", defaultValue = "0") int year,
            @RequestParam(name = "leagueType", defaultValue = "standard") String leagueType) {

        // Default to current year if not specified (or invalid)
        if (year <= 0) {
            year = java.time.Year.now().getValue();
        }

        log.info("GET /api/players — sources={}, year={}, leagueType={}", sources, year, leagueType);

        try {
            PlayerApiResponse response = playerService.getPlayerRankings(sources, year, leagueType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching player rankings: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * GET /api/sources
     *
     * Returns the list of available data source IDs that the frontend
     * can request data from.
     */
    @GetMapping("/sources")
    public ResponseEntity<?> getAvailableSources() {
        return ResponseEntity.ok(playerService.getAvailableSourceIds());
    }
}
