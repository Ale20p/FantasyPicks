package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Stub scraper for Yahoo Fantasy expert rankings.
 * TODO: Implement using Yahoo's fantasy pages or API.
 */
@Component
public class YahooScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(YahooScraper.class);

    @Override
    public String getSourceId() {
        return "yahoo";
    }

    @Override
    public String getSourceName() {
        return "Yahoo";
    }

    @Override
    public List<PlayerRanking> scrapeRankings() throws ScrapingException {
        log.warn("Yahoo scraper is not yet implemented — returning empty results");
        return Collections.emptyList();
    }
}
