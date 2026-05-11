package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SleeperScraperTest {

    @Test
    public void testScrape() throws Exception {
        SleeperScraper scraper = new SleeperScraper();
        List<PlayerRanking> rankings = scraper.scrapeRankings(2024, "PPR");
        assertNotNull(rankings);
        System.out.println("Total players scraped: " + rankings.size());
        int defCount = 0;
        int kCount = 0;
        for (PlayerRanking pr : rankings) {
            if ("DEF".equals(pr.getPosition())) defCount++;
            if ("K".equals(pr.getPosition())) kCount++;
        }
        System.out.println("DEF count: " + defCount);
        System.out.println("K count: " + kCount);
    }
}
