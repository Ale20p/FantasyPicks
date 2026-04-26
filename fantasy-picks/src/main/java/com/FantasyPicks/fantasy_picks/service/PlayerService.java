package com.FantasyPicks.fantasy_picks.service;

import com.FantasyPicks.fantasy_picks.model.PlayerApiResponse;
import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.FantasyPicks.fantasy_picks.scraper.RankingScraper;
import com.FantasyPicks.fantasy_picks.scraper.ScrapingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates scraping from multiple sources, merges player data,
 * and computes the overall consensus ranking.
 */
@Service
public class PlayerService {

    private static final Logger log = LoggerFactory.getLogger(PlayerService.class);

    /**
     * All available scrapers, injected by Spring.
     * Spring auto-discovers every @Component that implements RankingScraper.
     */
    private final Map<String, RankingScraper> scraperMap;

    public PlayerService(List<RankingScraper> scrapers) {
        this.scraperMap = new LinkedHashMap<>();
        for (RankingScraper scraper : scrapers) {
            scraperMap.put(scraper.getSourceId(), scraper);
            log.info("Registered scraper: {} ({})", scraper.getSourceId(), scraper.getSourceName());
        }
    }

    /**
     * @return Set of all registered source IDs.
     */
    public Set<String> getAvailableSourceIds() {
        return scraperMap.keySet();
    }

    /**
     * Fetch player rankings from the requested sources for a specific season year,
     * merge them, compute overall rank, and return the API response.
     *
     * @param requestedSources Comma-separated source IDs (e.g. "fantasypros,espn")
     * @param year The season year to fetch rankings for (e.g. 2025, 2026)
     * @return PlayerApiResponse ready to be serialized to JSON
     */
    public PlayerApiResponse getPlayerRankings(String requestedSources, int year) {
        // Parse the requested source IDs
        Set<String> sourceIds = Arrays.stream(requestedSources.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        log.info("Fetching rankings from sources: {} for year: {}", sourceIds, year);

        // Scrape each requested source
        // Key = "playerName|team" for deduplication, Value = merged PlayerRanking
        Map<String, PlayerRanking> mergedPlayers = new LinkedHashMap<>();

        for (String sourceId : sourceIds) {
            RankingScraper scraper = scraperMap.get(sourceId);
            if (scraper == null) {
                log.warn("Unknown source requested: '{}' — skipping", sourceId);
                continue;
            }

            try {
                List<PlayerRanking> scraped = scraper.scrapeRankings(year);
                log.info("Source '{}' returned {} players", sourceId, scraped.size());

                for (PlayerRanking player : scraped) {
                    String key = buildPlayerKey(player);
                    PlayerRanking existing = mergedPlayers.get(key);

                    if (existing == null) {
                        // First time seeing this player — add to map
                        mergedPlayers.put(key, player);
                    } else {
                        // Merge: copy this source's ranking into the existing player
                        existing.getRankings().putAll(player.getRankings());
                    }
                }
            } catch (ScrapingException e) {
                log.error("Failed to scrape from '{}': {}", sourceId, e.getMessage());
                // Continue with other sources — partial data is better than none
            }
        }

        // Compute overall rank (average of all source rankings)
        List<PlayerRanking> playerList = new ArrayList<>(mergedPlayers.values());
        computeOverallRanks(playerList, sourceIds);

        // Sort by overall rank
        playerList.sort(Comparator.comparingInt(PlayerRanking::getOverallRank));

        log.info("Returning {} merged players from {} source(s)", playerList.size(), sourceIds.size());
        return new PlayerApiResponse(playerList);
    }

    /**
     * Compute the overall rank for each player based on the average of their
     * rankings across the selected sources.
     */
    private void computeOverallRanks(List<PlayerRanking> players, Set<String> sourceIds) {
        // First: compute average rank score for each player
        List<PlayerWithScore> scored = new ArrayList<>();
        for (PlayerRanking player : players) {
            double totalRank = 0;
            int sourceCount = 0;

            for (String sourceId : sourceIds) {
                Integer rank = player.getRankings().get(sourceId);
                if (rank != null) {
                    totalRank += rank;
                    sourceCount++;
                }
            }

            // Players not ranked by any selected source get a high penalty score
            double avgRank = sourceCount > 0 ? totalRank / sourceCount : 9999.0;
            scored.add(new PlayerWithScore(player, avgRank));
        }

        // Sort by average rank, then assign sequential overall ranks
        scored.sort(Comparator.comparingDouble(s -> s.avgRank));
        for (int i = 0; i < scored.size(); i++) {
            scored.get(i).player.setOverallRank(i + 1);
        }
    }

    /**
     * Build a deduplication key for a player.
     * Uses name + team to handle players with the same name on different teams.
     */
    private String buildPlayerKey(PlayerRanking player) {
        String name = player.getName() == null ? "" : player.getName().toLowerCase().trim();
        String team = player.getTeam() == null ? "" : player.getTeam().toLowerCase().trim();
        return name + "|" + team;
    }

    /**
     * Helper record for sorting players by average rank score.
     */
    private static class PlayerWithScore {
        final PlayerRanking player;
        final double avgRank;

        PlayerWithScore(PlayerRanking player, double avgRank) {
            this.player = player;
            this.avgRank = avgRank;
        }
    }
}
