package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Stub scraper for NFL.com Fantasy player rankings.
 * TODO: Implement scraping from NFL.com's fantasy rankings pages.
 */
@Component
public class NFLScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(NFLScraper.class);

    @Override
    public String getSourceId() {
        return "nfl";
    }

    @Override
    public String getSourceName() {
        return "NFL.com";
    }

    @Override
    public List<PlayerRanking> scrapeRankings() throws ScrapingException {
        log.warn("NFL.com scraper is not yet implemented — returning empty results");
        return Collections.emptyList();
    }
}
