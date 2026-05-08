package com.FantasyPicks.fantasy_picks;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.FantasyPicks.fantasy_picks.scraper.DraftSharksScraper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DraftSharksScraperTest {

    @Test
    void testScrapeRankings() throws Exception {
        DraftSharksScraper scraper = new DraftSharksScraper();
        List<PlayerRanking> rankings = scraper.scrapeRankings(2026, "ppr");

        assertNotNull(rankings, "Rankings should not be null");
        assertFalse(rankings.isEmpty(), "Rankings should not be empty");

        System.out.println("Fetched " + rankings.size() + " players from DraftSharks.");
        for (int i = 0; i < Math.min(10, rankings.size()); i++) {
            PlayerRanking p = rankings.get(i);
            System.out.println((i + 1) + ". " + p.getName() + " - " + p.getPosition() + " - " + p.getTeam()
                    + " (DS Rank: " + p.getRankings().get("draftsharks") + ")");
        }
    }
}
