package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Scrapes player consensus rankings from FantasyPros.
 *
 * Strategy:
 *   1. Fetch the rankings page via Jsoup
 *   2. Extract the embedded `var ecrData = {...};` JSON from a <script> tag
 *   3. Parse the JSON to pull out the players array
 *   4. Fallback: parse the HTML <table id="ranking-table"> directly if ecrData isn't found
 */
@Component
public class FantasyProsScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(FantasyProsScraper.class);

    private static final String SOURCE_ID = "fantasypros";
    private static final String SOURCE_NAME = "FantasyPros";
    private static final String RANKINGS_URL_TEMPLATE =
            "https://www.fantasypros.com/nfl/rankings/consensus-cheatsheets.php?year=%d";

    /**
     * Regex to extract the ecrData JSON object from the page's JavaScript.
     * Matches: var ecrData = { ... };
     * Uses a non-greedy match inside braces with DOTALL so it spans lines.
     */
    private static final Pattern ECR_DATA_PATTERN =
            Pattern.compile("var\\s+ecrData\\s*=\\s*(\\{.*?\\});", Pattern.DOTALL);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        String url = String.format(RANKINGS_URL_TEMPLATE, year);
        try {
            log.info("Fetching rankings from FantasyPros: {}", url);

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .get();

            // Try the embedded JSON approach first (faster, richer data)
            List<PlayerRanking> players = tryParseEcrData(doc);

            if (players != null && !players.isEmpty()) {
                log.info("Successfully parsed {} players from ecrData JSON", players.size());
                return players;
            }

            // Fallback: parse the HTML table directly
            log.warn("ecrData not found or empty — falling back to HTML table parsing");
            players = parseHtmlTable(doc);
            log.info("Parsed {} players from HTML table fallback", players.size());
            return players;

        } catch (IOException e) {
            throw new ScrapingException(SOURCE_ID,
                    "Failed to fetch FantasyPros rankings page: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PRIMARY: Parse ecrData JSON embedded in <script> tags
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> tryParseEcrData(Document doc) {
        // Search all <script> tags for ecrData
        Elements scripts = doc.select("script");

        for (Element script : scripts) {
            String scriptContent = script.data();
            if (!scriptContent.contains("ecrData")) {
                continue;
            }

            Matcher matcher = ECR_DATA_PATTERN.matcher(scriptContent);
            if (!matcher.find()) {
                continue;
            }

            String jsonStr = matcher.group(1);
            try {
                return parseEcrJson(jsonStr);
            } catch (Exception e) {
                log.error("Failed to parse ecrData JSON: {}", e.getMessage());
                return null;
            }
        }

        return null;
    }

    private List<PlayerRanking> parseEcrJson(String jsonStr) throws Exception {
        JsonNode root = objectMapper.readTree(jsonStr);
        JsonNode playersNode = root.path("players");

        if (!playersNode.isArray()) {
            log.warn("ecrData.players is not an array");
            return null;
        }

        List<PlayerRanking> results = new ArrayList<>();
        int rank = 1;

        for (JsonNode playerNode : playersNode) {
            String name = getTextOrDefault(playerNode, "player_name", null);
            if (name == null || name.isBlank()) {
                continue;
            }

            String team = getTextOrDefault(playerNode, "player_team_id", "FA");
            String position = getTextOrDefault(playerNode, "player_position_id", "");

            // If player_position_id is missing, try player_positions
            if (position.isEmpty()) {
                position = getTextOrDefault(playerNode, "player_positions", "");
                // player_positions can be comma-separated, take the first
                if (position.contains(",")) {
                    position = position.split(",")[0].trim();
                }
            }

            // rank_ecr is the expert consensus rank (float, round to int for display)
            int ecrRank = rank;  // default to list position
            if (playerNode.has("rank_ecr")) {
                JsonNode rankNode = playerNode.get("rank_ecr");
                if (rankNode.isNumber()) {
                    ecrRank = (int) Math.round(rankNode.asDouble());
                }
            }

            PlayerRanking player = new PlayerRanking(name, normalizePosition(position), team.toUpperCase());
            player.addSourceRanking(SOURCE_ID, ecrRank);

            results.add(player);
            rank++;
        }

        return results;
    }

    // ═══════════════════════════════════════════════════════════════
    // FALLBACK: Parse the HTML <table id="ranking-table">
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> parseHtmlTable(Document doc) throws ScrapingException {
        Element table = doc.selectFirst("table#ranking-table");
        if (table == null) {
            // Also try the common class
            table = doc.selectFirst("table.player-table");
        }
        if (table == null) {
            throw new ScrapingException(SOURCE_ID,
                    "Could not find the rankings table on the FantasyPros page");
        }

        Elements rows = table.select("tbody tr");
        List<PlayerRanking> results = new ArrayList<>();
        int rank = 1;

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 4) {
                continue;
            }

            // Typical FantasyPros table structure:
            // td[0] = Rank, td[1] = Player name (may contain team/pos info), td[2] = Pos, td[3+] = stats
            String name = extractPlayerName(cells.get(1));
            String team = extractTeamFromCell(cells.get(1));
            String position = cells.size() > 2 ? cells.get(2).text().trim() : "";

            if (name == null || name.isBlank()) {
                continue;
            }

            PlayerRanking player = new PlayerRanking(name, normalizePosition(position), team.toUpperCase());
            player.addSourceRanking(SOURCE_ID, rank);
            results.add(player);
            rank++;
        }

        return results;
    }

    /**
     * Extract the player name from a table cell.
     * FantasyPros cells often contain: <a class="player-name">Name</a> <small>TEAM</small>
     */
    private String extractPlayerName(Element cell) {
        Element nameLink = cell.selectFirst("a.player-name, a[class*=player]");
        if (nameLink != null) {
            return nameLink.text().trim();
        }
        // Fallback: take the first text node
        String text = cell.text().trim();
        // Remove trailing team abbreviation if present (e.g., "Patrick Mahomes KC")
        return text.replaceAll("\\s+[A-Z]{2,4}$", "").trim();
    }

    /**
     * Extract the team abbreviation from a cell that may contain <small>TEAM</small>.
     */
    private String extractTeamFromCell(Element cell) {
        Element small = cell.selectFirst("small, span.player-team");
        if (small != null) {
            return small.text().trim();
        }
        // Try to find a 2-4 letter uppercase suffix
        String text = cell.text().trim();
        java.util.regex.Matcher m = Pattern.compile("\\b([A-Z]{2,4})\\s*$").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "FA";
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════

    private String getTextOrDefault(JsonNode node, String field, String defaultVal) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultVal;
        }
        return child.asText(defaultVal);
    }

    /**
     * Normalize position strings to the standard abbreviations used by the frontend.
     */
    private String normalizePosition(String position) {
        if (position == null) return "";
        String upper = position.toUpperCase().trim();

        return switch (upper) {
            case "QB", "QUARTERBACK" -> "QB";
            case "RB", "RUNNING BACK", "HB" -> "RB";
            case "WR", "WIDE RECEIVER" -> "WR";
            case "TE", "TIGHT END" -> "TE";
            case "K", "PK", "KICKER" -> "K";
            case "DST", "DEF", "D/ST", "DEFENSE" -> "DEF";
            default -> upper;
        };
    }
}
