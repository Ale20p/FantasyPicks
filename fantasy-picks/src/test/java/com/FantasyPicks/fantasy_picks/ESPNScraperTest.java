package com.FantasyPicks.fantasy_picks;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.FantasyPicks.fantasy_picks.scraper.ESPNScraper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ESPNScraperTest {

    @Test
    void testScrapeRankings() throws Exception {
        ESPNScraper scraper = new ESPNScraper();
        List<PlayerRanking> rankings = scraper.scrapeRankings(2025);
        
        assertNotNull(rankings, "Rankings should not be null");
        assertFalse(rankings.isEmpty(), "Rankings should not be empty");
        
        System.out.println("Fetched " + rankings.size() + " players from ESPN.");
        for (int i = 0; i < Math.min(5, rankings.size()); i++) {
            PlayerRanking p = rankings.get(i);
            System.out.println(p.getName() + " - " + p.getPosition() + " - " + p.getTeam() + " (Rank: " + p.getRankings().get("espn") + ")");
        }
    }
}
