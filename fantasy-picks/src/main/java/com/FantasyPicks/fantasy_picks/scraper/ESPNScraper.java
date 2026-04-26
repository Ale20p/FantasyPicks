package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Stub scraper for ESPN Fantasy Football rankings.
 * TODO: Implement using ESPN's undocumented fantasy API endpoints.
 */
@Component
public class ESPNScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(ESPNScraper.class);

    @Override
    public String getSourceId() {
        return "espn";
    }

    @Override
    public String getSourceName() {
        return "ESPN";
    }

    @Override
    public List<PlayerRanking> scrapeRankings() throws ScrapingException {
        log.warn("ESPN scraper is not yet implemented — returning empty results");
        // TODO: Implement ESPN scraping
        //   ESPN uses JS-rendered pages so this will likely need Playwright
        //   or reverse-engineering their XHR endpoints at:
        //   https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/...
        return Collections.emptyList();
    }
}
