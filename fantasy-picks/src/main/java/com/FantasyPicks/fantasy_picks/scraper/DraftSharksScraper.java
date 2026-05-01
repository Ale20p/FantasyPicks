package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Scraper for DraftSharks.com rankings.
 * Uses Playwright to handle dynamic rendering (Alpine.js / HTMX).
 */
@Component
public class DraftSharksScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(DraftSharksScraper.class);
    private static final String SOURCE_ID = "draftsharks";
    private static final String SOURCE_NAME = "DraftSharks";

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<PlayerRanking> scrapeRankings(int year, String leagueType) throws ScrapingException {
        List<PlayerRanking> players = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            // Launch browser with custom user agent to avoid bot detection
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent(
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
                Page page = context.newPage();

                String url = getUrlForLeagueType(leagueType);
                log.info("Navigating to DraftSharks: {}", url);

                try {
                    // Navigate with a timeout
                    page.navigate(url, new Page.NavigateOptions().setTimeout(30000));

                    // Wait for the data to load.
                    page.waitForSelector("tbody[data-player-row], tr.player-row",
                            new Page.WaitForSelectorOptions().setTimeout(15000));

                    // Extra wait to allow dynamic content to fully render
                    page.waitForTimeout(2000);

                    // Extract data directly in the browser. This is much more robust than Jsoup
                    // parsing.
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> extractedData = (List<Map<String, Object>>) page.evaluate("() => {" +
                            "  const results = [];" +
                            "  const containers = document.querySelectorAll('tbody[data-player-row]');" +
                            "  " +
                            "  containers.forEach((container, index) => {" +
                            "    const name = container.getAttribute('data-player-name') || '';" +
                            "    const pos = container.getAttribute('data-fantasy-position') || '';" +
                            "    " +
                            "    // Extract team - usually in a span inside .team-position-logo-container" +
                            "    let team = 'FA';" +
                            "    const teamSpan = container.querySelector('.team-position-logo-container span');" +
                            "    if (teamSpan) team = teamSpan.textContent.trim();" +
                            "    " +
                            "    // Rank is often the 1-indexed count, but can be extracted from td.rank if present" +
                            "    let rank = index + 1;" +
                            "    const rankTd = container.querySelector('td.rank');" +
                            "    if (rankTd) {" +
                            "      const rankVal = parseInt(rankTd.textContent.replace(/[^0-9]/g, ''));" +
                            "      if (!isNaN(rankVal)) rank = rankVal;" +
                            "    }" +
                            "    " +
                            "    if (name) {" +
                            "      results.push({ name, position: pos, team, rank });" +
                            "    }" +
                            "  });" +
                            "  return results;" +
                            "}");

                    log.info("DraftSharks browser evaluation returned {} players", extractedData.size());

                    for (Map<String, Object> p : extractedData) {
                        String name = (String) p.get("name");
                        String pos = (String) p.get("position");
                        String team = (String) p.get("team");
                        int rank = (int) p.get("rank");

                        PlayerRanking player = new PlayerRanking(name, normalizePosition(pos), team);
                        player.addSourceRanking(SOURCE_ID, rank);
                        players.add(player);
                    }

                    if (players.isEmpty()) {
                        log.error("No players found on DraftSharks page. Check selectors.");
                        throw new ScrapingException(SOURCE_ID, "No player rankings found on page.");
                    }

                } catch (Exception e) {
                    log.error("Error during DraftSharks navigation or evaluation: {}", e.getMessage());
                    throw new ScrapingException(SOURCE_ID, "Failed to extract data from DraftSharks: " + e.getMessage(),
                            e);
                }
            } catch (Exception e) {
                log.error("DraftSharks browser session failed: {}", e.getMessage());
                throw new ScrapingException(SOURCE_ID, "Browser session failed: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("DraftSharks scraping failed: {}", e.getMessage());
            throw new ScrapingException(SOURCE_ID, "Scraping failed: " + e.getMessage(), e);
        }

        return players;
    }

    private String getUrlForLeagueType(String leagueType) {
        if (leagueType == null)
            return "https://www.draftsharks.com/rankings/ppr";
        switch (leagueType.toLowerCase()) {
            case "standard":
                return "https://www.draftsharks.com/rankings/standard";
            case "half_ppr":
                return "https://www.draftsharks.com/rankings/half-ppr";
            case "superflex":
                return "https://www.draftsharks.com/rankings/ppr-superflex";
            case "ppr":
            default:
                return "https://www.draftsharks.com/rankings/ppr";
        }
    }

    private String normalizePosition(String pos) {
        if (pos == null)
            return "N/A";
        pos = pos.toUpperCase().trim();
        // Remove numbers (e.g. WR1 -> WR)
        pos = pos.replaceAll("\\d+", "");
        if (pos.startsWith("QB"))
            return "QB";
        if (pos.startsWith("RB"))
            return "RB";
        if (pos.startsWith("WR"))
            return "WR";
        if (pos.startsWith("TE"))
            return "TE";
        if (pos.startsWith("K"))
            return "K";
        if (pos.startsWith("DEF") || pos.startsWith("DST"))
            return "DEF";
        return pos;
    }
}
