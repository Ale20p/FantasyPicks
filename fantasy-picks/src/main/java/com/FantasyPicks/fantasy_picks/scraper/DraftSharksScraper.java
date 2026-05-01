package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes player rankings from DraftSharks using a headless browser.
 *
 * Strategy:
 * 1. Launch a Playwright Chromium headless browser.
 * 2. Navigate to DraftSharks rankings URL.
 * 3. Wait for the data table to load (bypassing Cloudflare/bot protection).
 * 4. Extract the HTML and parse using Jsoup.
 */
@Component
public class DraftSharksScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(DraftSharksScraper.class);

    private static final String SOURCE_ID = "draftsharks";
    private static final String SOURCE_NAME = "DraftSharks";
    private static final String BASE_URL = "https://www.draftsharks.com/rankings";

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    private String getEndpointForLeagueType(String leagueType) {
        String slug = "";
        if (leagueType != null) {
            slug = switch (leagueType.toLowerCase()) {
                case "ppr" -> "ppr";
                case "half_ppr" -> "half-ppr";
                case "superflex" -> "superflex";
                default -> "";
            };
        }
        return "https://www.draftsharks.com/rankings/load-table?pprSuperflexSlug=" + slug + "&researchDepth=rankings";
    }

    @Override
    public List<PlayerRanking> scrapeRankings(int year, String leagueType) throws ScrapingException {
        String url = getEndpointForLeagueType(leagueType);
        log.info("Fetching rankings from DraftSharks using Playwright: {} (leagueType: {})", url, leagueType);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            
            try {
                // Navigate with a timeout to avoid hanging indefinitely
                page.navigate(url, new Page.NavigateOptions().setTimeout(30000));
                
                // Wait for the table rows with a specific timeout
                page.waitForSelector("tr.player-row", new Page.WaitForSelectorOptions().setTimeout(15000));
            } catch (com.microsoft.playwright.PlaywrightException e) {
                log.error("Playwright failed to load or find elements at {}: {}", url, e.getMessage());
                browser.close();
                throw new ScrapingException(SOURCE_ID, "Timeout or navigation error: " + e.getMessage(), e);
            }

            String html = page.content();
            browser.close();

            if (html == null || html.isBlank()) {
                log.error("DraftSharks returned null or blank HTML content.");
                throw new ScrapingException(SOURCE_ID, "Received empty HTML content from DraftSharks.");
            }

            log.info("DraftSharks HTML content received (length: {} characters). Starting parse...", html.length());

            Document doc = Jsoup.parse(html);
            List<PlayerRanking> players = parseHtmlTable(doc);

            if (players.isEmpty()) {
                // Log the first 1000 chars of HTML to help debug selector issues or blocks
                String snippet = html.length() > 1000 ? html.substring(0, 1000) : html;
                log.error("No players parsed from DraftSharks. HTML snippet: {}", snippet);
                throw new ScrapingException(SOURCE_ID, "DraftSharks page loaded but no valid player rankings could be parsed. Check server logs for details.");
            }

            return players;

        } catch (ScrapingException e) {
            throw e; // Rethrow our own exception
        } catch (Exception e) {
            log.error("Unexpected error during DraftSharks scraping", e);
            throw new ScrapingException(SOURCE_ID, "Unexpected error: " + e.getMessage(), e);
        }
    }

    private List<PlayerRanking> parseHtmlTable(Document doc) {
        List<PlayerRanking> results = new ArrayList<>();
        
        if (doc == null) return results;

        // Since we hit the HTMX endpoint directly, the response is just a list of <tr> elements
        // without the parent tbody. We can just select all tr elements with the class 'player-row'.
        Elements rows = doc.select("tr.player-row");
        log.info("Jsoup found {} potential player rows with selector 'tr.player-row'", rows.size());
        
        if (rows.isEmpty()) {
            log.warn("No player rows found in the fetched HTML for DraftSharks.");
            return results;
        }

        int rank = 1;
        for (Element row : rows) {
            try {
                String name = "";
                String xData = row.attr("x-data");
                if (xData != null && xData.contains("\"name\":\"")) {
                    int start = xData.indexOf("\"name\":\"") + 8;
                    int end = xData.indexOf("\"", start);
                    if (start > 7 && end > start) {
                        name = xData.substring(start, end);
                    }
                }
                
                // Fallback to data attribute
                if (name == null || name.isBlank()) {
                    name = row.attr("data-player-name").trim();
                }
                
                String position = row.attr("data-fantasy-position");
                if (position != null) {
                    position = position.trim();
                }
                
                String team = "FA";
                Element teamSpan = row.selectFirst(".team-position-logo-container span");
                if (teamSpan != null && !teamSpan.text().isBlank()) {
                    team = teamSpan.text().trim();
                }

                if (name == null || name.isBlank() || position == null || position.isBlank()) {
                    log.debug("Skipping invalid player row: name='{}', position='{}'", name, position);
                    continue;
                }

                PlayerRanking player = new PlayerRanking(name, normalizePosition(position), team.toUpperCase());
                player.addSourceRanking(SOURCE_ID, rank);
                results.add(player);
                rank++;
            } catch (Exception e) {
                log.warn("Failed to parse individual player row in DraftSharks: {}", e.getMessage());
                // Continue to next row instead of failing whole scrape
            }
        }

        return results;
    }

    private String normalizePosition(String position) {
        if (position == null) return "";
        return switch (position.toUpperCase()) {
            case "QB", "QUARTERBACK" -> "QB";
            case "RB", "RUNNING BACK", "HB" -> "RB";
            case "WR", "WIDE RECEIVER" -> "WR";
            case "TE", "TIGHT END" -> "TE";
            case "K", "PK", "KICKER" -> "K";
            case "DEF", "DST", "D/ST", "DEFENSE" -> "DEF";
            default -> position.toUpperCase();
        };
    }
}
