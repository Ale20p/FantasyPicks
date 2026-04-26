package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;

import java.util.List;

/**
 * Contract for all ranking data scrapers.
 * Each implementation handles one data source (ESPN, FantasyPros, etc.)
 */
public interface RankingScraper {

    /**
     * @return The unique source identifier (matches frontend source IDs:
     *         "fantasypros", "espn", "sleeper", "yahoo", "nfl", "cbs").
     */
    String getSourceId();

    /**
     * @return A human-readable display name for this source.
     */
    String getSourceName();

    /**
     * Scrape the latest player rankings from this source.
     *
     * @return A list of PlayerRanking objects. Each object should have:
     *         - name, position, team populated
     *         - a single entry in the rankings map for this source
     *         The overallRank is computed later by PlayerService after merging all sources.
     * @throws ScrapingException if the scraping operation fails
     */
    List<PlayerRanking> scrapeRankings() throws ScrapingException;
}
