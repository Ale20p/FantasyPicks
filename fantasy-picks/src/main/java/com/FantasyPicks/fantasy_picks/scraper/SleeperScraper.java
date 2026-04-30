package com.FantasyPicks.fantasy_picks.scraper;

import com.FantasyPicks.fantasy_picks.model.PlayerRanking;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Scrapes player rankings from the Sleeper public API.
 *
 * Strategy:
 * 1. Hit the public /v1/players/nfl endpoint (returns all NFL players as a JSON
 * object)
 * 2. Filter for fantasy-relevant, active, rostered players
 * 3. Sort by Sleeper's built-in "search_rank" field (lower = better)
 * 4. Re-number sequentially to produce a clean 1..N ranking
 *
 * Note: The response is large (~14 MB) containing ~10,000+ player entries.
 * Only fantasy-relevant positions on active rosters are kept.
 */
@Component
public class SleeperScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(SleeperScraper.class);

    private static final String SOURCE_ID = "sleeper";
    private static final String SOURCE_NAME = "Sleeper";
    private static final String API_URL = "https://api.sleeper.app/v1/players/nfl";

    /** Fantasy-relevant positions to keep */
    private static final Set<String> FANTASY_POSITIONS = Set.of("QB", "RB", "WR", "TE", "K", "PK", "DEF");

    /** Ignore players with search_rank at or above this threshold */
    private static final int MAX_SEARCH_RANK = 500;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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
        // Note: Sleeper's /v1/players/nfl endpoint does not support year-based
        // or league-type based filtering.
        // filtering.
        // It always returns the current active player roster with search_rank.
        // The year parameter is accepted for interface compliance but not used in the
        // URL.
        try {
            log.info("Fetching player data from Sleeper API: {} (year param {} ignored — Sleeper returns current data)",
                    API_URL, year);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(45)) // Large response needs time
                    .header("Accept", "application/json")
                    .header("User-Agent", "FantasyPicks/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new ScrapingException(SOURCE_ID,
                        "Sleeper API returned HTTP " + response.statusCode());
            }

            log.info("Sleeper API response received ({} bytes), parsing...",
                    response.body().length());

            JsonNode root = objectMapper.readTree(response.body());

            // The root is a JSON object: { "playerId": { ... }, "playerId2": { ... }, ... }
            List<PlayerRanking> players = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode playerNode = entry.getValue();

                PlayerRanking ranking = parsePlayer(playerNode);
                if (ranking != null) {
                    players.add(ranking);
                }
            }

            // Sort by the raw search_rank, then re-number 1..N
            players.sort(Comparator.comparingInt(p -> p.getRankings().get(SOURCE_ID)));
            for (int i = 0; i < players.size(); i++) {
                players.get(i).addSourceRanking(SOURCE_ID, i + 1);
            }

            log.info("Sleeper: parsed {} fantasy-relevant players", players.size());
            return players;

        } catch (ScrapingException e) {
            throw e;
        } catch (Exception e) {
            throw new ScrapingException(SOURCE_ID,
                    "Failed to fetch from Sleeper API: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PARSING
    // ═══════════════════════════════════════════════════════════════

    private PlayerRanking parsePlayer(JsonNode node) {
        // Must be active
        if (!node.path("active").asBoolean(false))
            return null;

        // Determine position
        String position = resolvePosition(node);
        if (position == null || !FANTASY_POSITIONS.contains(position))
            return null;

        // Must be on a team
        String team = node.path("team").asText("");
        if (team.isEmpty() || "null".equals(team))
            return null;

        // Must have a reasonable search_rank
        int searchRank = node.path("search_rank").asInt(Integer.MAX_VALUE);
        if (searchRank >= MAX_SEARCH_RANK)
            return null;

        // Must have a name
        String fullName = node.path("full_name").asText("");
        if (fullName.isBlank()) {
            // Fallback for DEF entries which may use last_name as the team name
            String firstName = node.path("first_name").asText("");
            String lastName = node.path("last_name").asText("");
            fullName = (firstName + " " + lastName).trim();
            if (fullName.isBlank())
                return null;
        }

        PlayerRanking ranking = new PlayerRanking(
                fullName,
                normalizePosition(position),
                team.toUpperCase());
        // Store raw search_rank initially; will be re-numbered after sorting
        ranking.addSourceRanking(SOURCE_ID, searchRank);
        return ranking;
    }

    /**
     * Resolve the player's primary fantasy position.
     * Prefers fantasy_positions array over the general position field.
     */
    private String resolvePosition(JsonNode node) {
        JsonNode fantasyPos = node.path("fantasy_positions");
        if (fantasyPos.isArray() && !fantasyPos.isEmpty()) {
            String pos = fantasyPos.get(0).asText("").toUpperCase();
            if (!pos.isEmpty())
                return pos;
        }
        String pos = node.path("position").asText("");
        return pos.isEmpty() ? null : pos.toUpperCase();
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