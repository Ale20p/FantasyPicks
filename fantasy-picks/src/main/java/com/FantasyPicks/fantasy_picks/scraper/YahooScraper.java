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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes player ADP (Average Draft Position) from Yahoo Fantasy Football.
 *
 * Strategy:
 *   1. Fetch Yahoo's public draft analysis page at
 *      football.fantasysports.yahoo.com/f1/draftanalysis
 *   2. Parse the HTML table for player name, position, team, and ADP ranking
 *
 * Yahoo's draft analysis page shows Average Draft Position (ADP) data
 * which serves as Yahoo's effective player ranking. The data is partially
 * server-rendered, so Jsoup can extract it — but the ADP table itself may
 * require JavaScript for full rendering.
 *
 * If the standard table parsing fails, we fall back to scraping the
 * publicly accessible Yahoo Sports player rankings page at
 * sports.yahoo.com/nfl/players/ which is lighter and more reliably
 * server-rendered.
 *
 * Note: Yahoo's fantasy pages may require authentication for full access.
 *       If scraping fails, the error is reported gracefully.
 */
@Component
public class YahooScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(YahooScraper.class);

    private static final String SOURCE_ID = "yahoo";
    private static final String SOURCE_NAME = "Yahoo";

    /** Yahoo draft analysis — ADP data */
    private static final String DRAFT_ANALYSIS_URL =
            "https://football.fantasysports.yahoo.com/f1/draftanalysis?pos=ALL&sort=DA_AP";

    /**
     * Regex to parse player entries from Yahoo's page.
     * Matches patterns like "Player Name (TEAM - POS)" which Yahoo uses in tooltips.
     */
    private static final Pattern PLAYER_PATTERN =
            Pattern.compile("([A-Za-z'.\\-\\s]+?)\\s*\\(([A-Z]{2,4})\\s*-\\s*(QB|RB|WR|TE|K|DEF)\\)");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

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
        // Note: Yahoo's draft analysis page does not support year-based querying via URL params.
        // The year parameter is accepted for interface compliance.
        log.info("Yahoo: requested year={} (Yahoo ADP page does not support year filtering)", year);
        // Try the draft analysis page first
        List<PlayerRanking> players = tryDraftAnalysisPage();
        if (players != null && !players.isEmpty()) {
            log.info("Yahoo: successfully scraped {} players from draft analysis", players.size());
            return players;
        }

        // Fallback: try parsing any player data we can find
        log.warn("Yahoo: draft analysis table not parseable. " +
                "This page likely requires JavaScript rendering (Playwright) " +
                "or authentication for full data access.");

        throw new ScrapingException(SOURCE_ID,
                "Yahoo Fantasy rankings require JavaScript rendering or authentication. " +
                "The draft analysis page at football.fantasysports.yahoo.com/f1/draftanalysis " +
                "does not serve its ADP table in server-rendered HTML. " +
                "Consider implementing Playwright support for this source.");
    }

    // ═══════════════════════════════════════════════════════════════
    // DRAFT ANALYSIS PAGE
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> tryDraftAnalysisPage() {
        try {
            log.info("Yahoo: fetching draft analysis from {}", DRAFT_ANALYSIS_URL);

            Document doc = Jsoup.connect(DRAFT_ANALYSIS_URL)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .followRedirects(true)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get();

            // Check if we were redirected to a login page
            String title = doc.title().toLowerCase();
            if (title.contains("sign in") || title.contains("login")) {
                log.warn("Yahoo: redirected to login page — authentication required");
                return null;
            }

            // Strategy 1: Parse the standard ADP table
            List<PlayerRanking> players = parseAdpTable(doc);
            if (!players.isEmpty()) return players;

            // Strategy 2: Look for player data in any table on the page
            players = parseAnyPlayerTable(doc);
            if (!players.isEmpty()) return players;

            // Strategy 3: Extract from structured data / links
            players = extractFromPlayerLinks(doc);
            if (!players.isEmpty()) return players;

            return null;

        } catch (IOException e) {
            log.warn("Yahoo: failed to fetch draft analysis page — {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PARSING STRATEGIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Strategy 1: Parse Yahoo's standard ADP table.
     * Expected columns: Rank | Player (with team/pos info) | ADP | Avg Round | % Drafted
     */
    private List<PlayerRanking> parseAdpTable(Document doc) {
        List<PlayerRanking> results = new ArrayList<>();

        // Try known table selectors
        Elements rows = doc.select("table#draftanalysistable tbody tr");
        if (rows.isEmpty()) rows = doc.select("table.Table tbody tr");
        if (rows.isEmpty()) rows = doc.select("#draftanalysis table tbody tr");
        if (rows.isEmpty()) rows = doc.select("section table tbody tr");

        int rank = 1;
        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            // Look for player name + team/position in the row
            PlayerInfo info = extractPlayerInfo(row);
            if (info == null) continue;

            PlayerRanking ranking = new PlayerRanking(info.name, info.position, info.team);
            ranking.addSourceRanking(SOURCE_ID, rank);
            results.add(ranking);
            rank++;
        }

        return results;
    }

    /**
     * Strategy 2: Look for player data in any table.
     */
    private List<PlayerRanking> parseAnyPlayerTable(Document doc) {
        List<PlayerRanking> results = new ArrayList<>();

        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements rows = table.select("tbody tr");
            int playerCount = 0;

            for (Element row : rows) {
                PlayerInfo info = extractPlayerInfo(row);
                if (info != null) playerCount++;
            }

            // If this table has a significant number of player entries, parse it fully
            if (playerCount >= 10) {
                int rank = 1;
                for (Element row : rows) {
                    PlayerInfo info = extractPlayerInfo(row);
                    if (info == null) continue;

                    PlayerRanking ranking = new PlayerRanking(info.name, info.position, info.team);
                    ranking.addSourceRanking(SOURCE_ID, rank);
                    results.add(ranking);
                    rank++;
                }
                break;
            }
        }

        return results;
    }

    /**
     * Strategy 3: Extract player info from links containing player data.
     * Yahoo pages often have links like /nfl/players/12345 with tooltip text
     * containing "Player Name (TEAM - POS)".
     */
    private List<PlayerRanking> extractFromPlayerLinks(Document doc) {
        List<PlayerRanking> results = new ArrayList<>();

        Elements links = doc.select("a[href*=players]");
        int rank = 1;

        for (Element link : links) {
            String text = link.text().trim();
            String title = link.attr("title").trim();

            // Try to match "Player Name (TEAM - POS)" from title or parent text
            String toCheck = title.isEmpty() ? text : title;
            Matcher m = PLAYER_PATTERN.matcher(toCheck);
            if (m.find()) {
                String name = m.group(1).trim();
                String team = m.group(2).trim();
                String position = m.group(3).trim();

                PlayerRanking ranking = new PlayerRanking(name, position, team);
                ranking.addSourceRanking(SOURCE_ID, rank);
                results.add(ranking);
                rank++;
            }
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Extract player name, team, and position from a table row.
     * Handles Yahoo's typical patterns of embedding this info.
     */
    private PlayerInfo extractPlayerInfo(Element row) {
        // Look for player name in anchor tags
        Element nameLink = row.selectFirst("a[href*=player], a.Fw-b, a.name");
        if (nameLink == null) {
            nameLink = row.selectFirst("a");
        }
        if (nameLink == null) return null;

        String name = nameLink.text().trim();
        if (name.length() < 3) return null;

        // Look for team and position
        String team = "FA";
        String position = "";

        // Yahoo often puts "TEAM - POS" in a span near the player name
        Element details = row.selectFirst("span.Fz-xxs, span.ysf-player-detail, span[class*=team]");
        if (details != null) {
            String detailText = details.text().trim();
            String[] parts = detailText.split("\\s*-\\s*");
            if (parts.length >= 2) {
                team = parts[0].trim().toUpperCase();
                position = normalizePosition(parts[1].trim().toUpperCase());
            }
        }

        // If position still empty, check for position text in the row
        if (position.isEmpty()) {
            Element posEl = row.selectFirst("td.Pos, span.pos, td:nth-child(3)");
            if (posEl != null) {
                String posText = posEl.text().trim().toUpperCase();
                if (isPosition(posText)) position = normalizePosition(posText);
            }
        }

        // Must have at least a name to be valid
        if (name.isBlank()) return null;

        return new PlayerInfo(name, team, position);
    }

    private boolean isPosition(String text) {
        return switch (text.toUpperCase()) {
            case "QB", "RB", "WR", "TE", "K", "DEF", "DST", "D/ST", "PK" -> true;
            default -> false;
        };
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

    /**
     * Simple record-like holder for extracted player info.
     */
    private static class PlayerInfo {
        final String name;
        final String team;
        final String position;

        PlayerInfo(String name, String team, String position) {
            this.name = name;
            this.team = team;
            this.position = position;
        }
    }
}
