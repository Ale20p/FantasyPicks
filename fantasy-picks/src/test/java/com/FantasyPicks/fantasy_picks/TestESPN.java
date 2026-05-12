package com.FantasyPicks.fantasy_picks;

import com.FantasyPicks.fantasy_picks.scraper.ESPNScraper;
import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import java.util.List;

public class TestESPN {
    public static void main(String[] args) {
        ESPNScraper scraper = new ESPNScraper();
        try {
            List<PlayerRanking> rankings = scraper.scrapeRankings(2026, "ppr");
            System.out.println("Scraped " + rankings.size() + " players");
            for (int i = 0; i < Math.min(10, rankings.size()); i++) {
                System.out.println(rankings.get(i).getName() + " - " + rankings.get(i).getRankings().get("espn"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
