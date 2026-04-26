package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Stub scraper for Sleeper app consensus rankings.
 * TODO: Implement using Sleeper's public API or page scraping.
 */
@Component
public class SleeperScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(SleeperScraper.class);

    @Override
    public String getSourceId() {
        return "sleeper";
    }

    @Override
    public String getSourceName() {
        return "Sleeper";
    }

    @Override
    public List<PlayerRanking> scrapeRankings() throws ScrapingException {
        log.warn("Sleeper scraper is not yet implemented — returning empty results");
        return Collections.emptyList();
    }
}
