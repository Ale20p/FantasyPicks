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
 * 1. Hit the specific /projections/nfl/{year} endpoint based on the league type (PPR, Half-PPR, Standard)
 * 2. Filter for fantasy-relevant, active, rostered players with a valid ADP.
 * 3. Use the returned sorted order (which is ordered by the requested adp format)
 * 4. Assign sequential 1..N ranking
 */
@Component
public class SleeperScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(SleeperScraper.class);

    private static final String SOURCE_ID = "sleeper";
    private static final String SOURCE_NAME = "Sleeper";

    /** Fantasy-relevant positions to keep */
    private static final Set<String> FANTASY_POSITIONS = Set.of("QB", "RB", "WR", "TE", "K", "PK", "DEF");

    /** Ignore players with ADP at or above this threshold */
    private static final double MAX_ADP = 500.0;

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

    private String getAdpKeyForLeagueType(String leagueType) {
        if (leagueType == null) return "adp_ppr";
        return switch (leagueType.toLowerCase()) {
            case "half_ppr" -> "adp_half_ppr";
            case "standard" -> "adp_std";
            default -> "adp_ppr";
        };
    }

    private String getUrlForLeagueType(int year, String leagueType) {
        String base = "https://api.sleeper.com/projections/nfl/" + year + 
                      "?season_type=regular&position[]=DEF&position[]=K&position[]=QB&position[]=RB&position[]=TE&position[]=WR&order_by=";
        return base + getAdpKeyForLeagueType(leagueType);
    }

    @Override
    public List<PlayerRanking> scrapeRankings(int year, String leagueType) throws ScrapingException {
        try {
            String apiUrl = getUrlForLeagueType(year, leagueType);
            log.info("Fetching player data from Sleeper API: {}", apiUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(45)) 
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

            // The root is a JSON array
            List<PlayerRanking> players = new ArrayList<>();
            
            if (root.isArray()) {
                String adpKey = getAdpKeyForLeagueType(leagueType);
                int rank = 1;
                for (JsonNode node : root) {
                    JsonNode statsNode = node.path("stats");
                    double adp = statsNode.path(adpKey).asDouble(999.0);
                    if (adp >= MAX_ADP) continue;

                    PlayerRanking ranking = parsePlayer(node);
                    if (ranking != null) {
                        ranking.addSourceRanking(SOURCE_ID, rank++);
                        players.add(ranking);
                    }
                }
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
        JsonNode playerNode = node.path("player");
        if (playerNode.isMissingNode() || playerNode.isNull()) {
            return null;
        }

        // Determine position
        String position = resolvePosition(playerNode);
        if (position == null || !FANTASY_POSITIONS.contains(position))
            return null;

        // Must be on a team (check player node first, fallback to root node)
        String team = playerNode.path("team").asText("");
        if (team.isEmpty() || "null".equals(team)) {
            team = node.path("team").asText("");
        }
        if (team.isEmpty() || "null".equals(team))
            return null;

        // Must have a name
        String fullName = playerNode.path("full_name").asText("");
        if (fullName.isBlank()) {
            String firstName = playerNode.path("first_name").asText("");
            String lastName = playerNode.path("last_name").asText("");
            fullName = (firstName + " " + lastName).trim();
            if (fullName.isBlank()) {
                // If it's a DEF and we have no name, use team abbreviation + " DEF"
                if ("DEF".equals(normalizePosition(position))) {
                    fullName = team + " DEF";
                } else {
                    return null;
                }
            }
        }

        PlayerRanking ranking = new PlayerRanking(
                fullName,
                normalizePosition(position),
                team.toUpperCase());
        return ranking;
    }

    /**
     * Resolve the player's primary fantasy position.
     * Prefers fantasy_positions array over the general position field.
     */
    private String resolvePosition(JsonNode playerNode) {
        JsonNode fantasyPos = playerNode.path("fantasy_positions");
        if (fantasyPos.isArray() && !fantasyPos.isEmpty()) {
            String pos = fantasyPos.get(0).asText("").toUpperCase();
            if (!pos.isEmpty())
                return pos;
        }
        String pos = playerNode.path("position").asText("");
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