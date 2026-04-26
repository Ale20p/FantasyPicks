package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes player rankings from NFL.com's Fantasy Research Rankings page.
 *
 * Strategy:
 * 1. Fetch individual position pages from fantasy.nfl.com/research/rankings
 * with position filters (QB, RB, WR, TE, K, DEF) using season projected stats
 * 2. Parse the HTML table for player name, team, and position ranking
 * 3. Merge all positions into a single list ranked by projected fantasy points
 *
 * Note: NFL.com serves some ranking data server-side in the initial HTML,
 * making Jsoup viable. The projected stats view provides the most
 * complete player listings.
 */
@Component
public class NFLScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(NFLScraper.class);

    private static final String SOURCE_ID = "nfl";
    private static final String SOURCE_NAME = "NFL.com";

    /**
     * URL template for position-specific season projected stats.
     * %s = position (QB, RB, WR, TE, K, DEF)
     * %d = season year
     */
    private static final String URL_TEMPLATE = "https://fantasy.nfl.com/research/rankings?leagueId=0&position=%s" +
            "&statSeason=%d&statType=seasonProjectedStats&statCategory=projectedStats";

    /** Overall rankings URL (all positions) for the season stats view */
    private static final String OVERALL_URL_TEMPLATE = "https://fantasy.nfl.com/research/rankings?leagueId=0" +
            "&statSeason=%d&statType=seasonStats";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private static final String[] POSITIONS = { "QB", "RB", "WR", "TE", "K", "DEF" };

    @Override
    public String getSourceId() {
        return SOURCE_ID;
    }

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<PlayerRanking> scrapeRankings(int year) throws ScrapingException {
        // Try scraping per-position pages and merge
        List<PlayerRanking> allPlayers = new ArrayList<>();

        for (String position : POSITIONS) {
            try {
                List<PlayerRanking> posPlayers = scrapePositionPage(position, year);
                if (posPlayers.isEmpty()) {
                    // Try previous season
                    posPlayers = scrapePositionPage(position, year - 1);
                }
                allPlayers.addAll(posPlayers);
            } catch (Exception e) {
                log.warn("NFL.com: failed to scrape position {} — {}", position, e.getMessage());
                // Continue with other positions
            }
        }

        if (allPlayers.isEmpty()) {
            // Fallback: try the overall rankings page
            allPlayers = scrapeOverallPage(year);
            if (allPlayers.isEmpty()) {
                allPlayers = scrapeOverallPage(year - 1);
            }
        }

        if (allPlayers.isEmpty()) {
            throw new ScrapingException(SOURCE_ID,
                    "Could not parse any player data from NFL.com rankings pages. " +
                            "The page structure may have changed or data may not be available yet.");
        }

        // Assign sequential overall rankings (1..N)
        for (int i = 0; i < allPlayers.size(); i++) {
            allPlayers.get(i).addSourceRanking(SOURCE_ID, i + 1);
        }

        log.info("NFL.com: scraped {} total players across all positions", allPlayers.size());
        return allPlayers;
    }

    // ═══════════════════════════════════════════════════════════════
    // POSITION-SPECIFIC SCRAPING
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> scrapePositionPage(String position, int season)
            throws IOException {
        String url = String.format(URL_TEMPLATE, position, season);
        log.info("NFL.com: fetching {} rankings from {}", position, url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .followRedirects(true)
                .get();

        return parseRankingsTable(doc, position);
    }

    private List<PlayerRanking> scrapeOverallPage(int season) {
        String url = String.format(OVERALL_URL_TEMPLATE, season);
        log.info("NFL.com: fetching overall rankings from {}", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .followRedirects(true)
                    .get();

            return parseRankingsTable(doc, null);
        } catch (IOException e) {
            log.warn("NFL.com: failed to fetch overall page — {}", e.getMessage());
            return List.of();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TABLE PARSING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Parse the rankings table from an NFL.com fantasy research page.
     *
     * NFL.com's table structure typically has:
     * - Player name in an anchor tag (class="playerName" or within a player cell)
     * - Team abbreviation near the player name (in a <em> or <span>)
     * - Position is either known from the page filter or listed in the table
     */
    private List<PlayerRanking> parseRankingsTable(Document doc, String knownPosition) {
        List<PlayerRanking> results = new ArrayList<>();

        // Try several selector strategies for NFL.com's table
        Elements rows = doc.select("table tbody tr");
        if (rows.isEmpty()) {
            rows = doc.select(".tableWrap table tr");
        }
        if (rows.isEmpty()) {
            rows = doc.select("#researchRankings table tr");
        }

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 2)
                continue;

            // Try to find the player name
            String name = extractPlayerName(row);
            if (name == null || name.isBlank())
                continue;

            // Try to find the team
            String team = extractTeam(row);

            // Position
            String position = knownPosition != null
                    ? knownPosition
                    : extractPosition(row);

            PlayerRanking ranking = new PlayerRanking(
                    name,
                    normalizePosition(position),
                    team.toUpperCase());
            // Temporary rank — will be re-assigned after merging
            ranking.addSourceRanking(SOURCE_ID, results.size() + 1);
            results.add(ranking);
        }

        log.info("NFL.com: parsed {} players for position {}", results.size(),
                knownPosition != null ? knownPosition : "ALL");
        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // ELEMENT EXTRACTION HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String extractPlayerName(Element row) {
        // Try common NFL.com player name selectors
        Element nameEl = row.selectFirst("a.playerName");
        if (nameEl != null)
            return nameEl.text().trim();

        nameEl = row.selectFirst("a[class*=playerName]");
        if (nameEl != null)
            return nameEl.text().trim();

        nameEl = row.selectFirst("a[href*=players/card]");
        if (nameEl != null)
            return nameEl.text().trim();

        nameEl = row.selectFirst("a[href*=player]");
        if (nameEl != null)
            return nameEl.text().trim();

        // Fallback: try the second <td> (first is usually rank number)
        Elements cells = row.select("td");
        if (cells.size() >= 2) {
            Element cell = cells.get(1);
            // Get just the text, stripping out team/position annotations
            Element anchor = cell.selectFirst("a");
            if (anchor != null)
                return anchor.text().trim();
            return cell.ownText().trim();
        }

        return null;
    }

    private String extractTeam(Element row) {
        // NFL.com uses <em> tags with format "POSITION - TEAM" (e.g. "QB - NYG")
        Element emEl = row.selectFirst("em");
        if (emEl != null) {
            String text = emEl.text().trim();
            if (text.contains(" - ")) {
                String[] parts = text.split("\\s*-\\s*");
                // Format is "POS - TEAM", so team is the SECOND part
                if (parts.length >= 2) {
                    String teamPart = parts[1].trim().toUpperCase();
                    if (teamPart.length() >= 2 && teamPart.length() <= 4) {
                        return teamPart;
                    }
                }
            }
            // Might be just a team abbreviation
            String upper = text.toUpperCase().trim();
            if (upper.length() >= 2 && upper.length() <= 4 && !isPosition(upper)) {
                return upper;
            }
        }

        // Fallback: extract from parent div class like "c c-nyg"
        Element containerDiv = row.selectFirst("div[class*=c-]");
        if (containerDiv != null) {
            String className = containerDiv.className();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\bc-([a-z]{2,4})\\b")
                    .matcher(className);
            if (m.find()) {
                return m.group(1).toUpperCase();
            }
        }

        // Look for a span with team class
        Element teamSpan = row.selectFirst("span[class*=team], span.teamName");
        if (teamSpan != null)
            return teamSpan.text().trim();

        return "FA";
    }

    private String extractPosition(Element row) {
        // NFL.com uses <em> tags with format "POSITION - TEAM" (e.g. "QB - NYG")
        Element emEl = row.selectFirst("em");
        if (emEl != null) {
            String text = emEl.text().trim().toUpperCase();
            if (text.contains("-")) {
                String[] parts = text.split("\\s*-\\s*");
                // Format is "POS - TEAM", so position is the FIRST part
                if (parts.length >= 2) {
                    String posPart = parts[0].trim();
                    if (isPosition(posPart))
                        return posPart;
                }
            }
            if (isPosition(text))
                return text;
        }
        return "";
    }

    private boolean isPosition(String text) {
        return switch (text.toUpperCase()) {
            case "QB", "RB", "WR", "TE", "K", "DEF", "DST", "D/ST" -> true;
            default -> false;
        };
    }

    private String normalizePosition(String position) {
        if (position == null)
            return "";
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
