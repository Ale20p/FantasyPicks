package com.FantasyPicks.fantasy_picks.scraper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Scraper for DraftSharks.com rankings.
 * Uses Playwright to handle dynamic rendering (Alpine.js / HTMX).
 */
@Component
public class DraftSharksScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(DraftSharksScraper.class);
    private static final String SOURCE_ID = "draftsharks";
    private static final String SOURCE_NAME = "DraftSharks";

    /** Positions we care about in fantasy — offensive skill players, kickers, and team defense. */
    private static final Set<String> ALLOWED_POSITIONS = Set.of("QB", "RB", "WR", "TE", "K", "DEF");

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
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .setViewportSize(1280, 800)); // Set to pretend to be a desktop monitor

                context.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

                Page page = context.newPage();

                String url = getUrlForLeagueType(leagueType);
                log.info("Navigating to DraftSharks: {}", url);

                try {
                    // Navigate with a timeout
                    page.navigate(url, new Page.NavigateOptions().setTimeout(30000));

                    // Scroll down the page to trigger lazy-loaded dynamic content
                    log.info("Scrolling down to trigger DraftSharks lazy loading...");
                    for (int i = 0; i < 4; i++) {
                        page.mouse().wheel(0, 800);
                        page.waitForTimeout(500);
                    }

                    // Wait for the data to load
                    page.waitForSelector("tbody[data-player-row], tr.player-row",
                            new Page.WaitForSelectorOptions().setTimeout(15000));

                    // Extra wait to allow dynamic content to fully render
                    page.waitForTimeout(2000);

                    // Take a screenshot for debugging
                    // page.screenshot(new
                    // Page.ScreenshotOptions().setPath(java.nio.file.Paths.get("headless-debug.png")));

                    // Extract data directly in the browser via JavaScript evaluation.
                    // IMPORTANT: No single-line comments (//) in this JS string — the Java
                    // concatenation produces one long line, so // would swallow all subsequent
                    // code.
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> extractedData = (List<Map<String, Object>>) page.evaluate(
                            "() => {\n" +
                                    "  const results = [];\n" +
                                    "  const containers = document.querySelectorAll('tbody[data-player-row]');\n" +
                                    "  containers.forEach((container, index) => {\n" +
                                    "    const name = container.getAttribute('data-player-name') || '';\n" +
                                    "    const pos = container.getAttribute('data-fantasy-position') || '';\n" +
                                    "    let team = 'FA';\n" +
                                    "    const teamSpan = container.querySelector('.team-position-logo-container span');\n"
                                    +
                                    "    if (teamSpan) team = teamSpan.textContent.trim();\n" +
                                    "    let rank = index + 1;\n" +
                                    "    const rankTd = container.querySelector('td.rank');\n" +
                                    "    if (rankTd) {\n" +
                                    "      const rankVal = parseInt(rankTd.textContent.replace(/[^0-9]/g, ''));\n" +
                                    "      if (!isNaN(rankVal)) rank = rankVal;\n" +
                                    "    }\n" +
                                    "    if (name) {\n" +
                                    "      results.push({ name: name, position: pos, team: team, rank: rank });\n" +
                                    "    }\n" +
                                    "  });\n" +
                                    "  return results;\n" +
                                    "}");

                    log.info("DraftSharks browser evaluation returned {} players", extractedData.size());

                    int skippedCount = 0;
                    for (Map<String, Object> p : extractedData) {
                        String name = (String) p.get("name");
                        String pos = normalizePosition((String) p.get("position"));
                        String team = (String) p.get("team");
                        int rank = (int) p.get("rank");

                        // Filter out individual defensive players (LB, CB, S, DE, DT, etc.)
                        if (!ALLOWED_POSITIONS.contains(pos)) {
                            skippedCount++;
                            continue;
                        }

                        PlayerRanking player = new PlayerRanking(name, pos, team);
                        player.addSourceRanking(SOURCE_ID, rank);
                        players.add(player);
                    }

                    if (skippedCount > 0) {
                        log.info("Filtered out {} non-fantasy positions (defensive players, etc.)", skippedCount);
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
