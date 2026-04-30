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
 * Scrapes player rankings from ESPN's undocumented Fantasy Football API.
 *
 * Strategy:
 * 1. Hit the /leaguefree endpoint with the kona_player_info view
 * 2. Use the X-Fantasy-Filter header to request players sorted by STANDARD
 * draft rank
 * 3. Parse the JSON response for player name, position, team, and rank
 *
 * ESPN API notes:
 * - Positions: 1=QB, 2=RB, 3=WR, 4=TE, 5=K, 16=D/ST
 * - Teams use numeric IDs that must be mapped to abbreviations
 * - The API is undocumented and may change; robust error handling is critical
 * - If the season endpoint isn't available yet, falls back to the previous year
 */
@Component
public class ESPNScraper implements RankingScraper {

    private static final Logger log = LoggerFactory.getLogger(ESPNScraper.class);

    private static final String SOURCE_ID = "espn";
    private static final String SOURCE_NAME = "ESPN";

    private static final String API_TEMPLATE = "https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/seasons/%d/segments/0/leaguefree?view=kona_player_info";

    /** Filter header requesting players sorted by STANDARD draft rank, top 300 */
    private static final String FANTASY_FILTER_TEMPLATE = "{\"players\":{\"filterSlotIds\":{\"value\":[0,2,4,6,16,17]},"
            +
            "\"sortDraftRanks\":{\"sortPriority\":100,\"sortAscending\":true,\"value\":\"STANDARD\"}," +
            "\"limit\":%d,\"offset\":0}}";

    private static final int PLAYER_LIMIT = 300;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36";

    /** ESPN team ID → standard abbreviation */
    private static final Map<Integer, String> TEAM_MAP = Map.ofEntries(
            Map.entry(0, "FA"),
            Map.entry(1, "ATL"), Map.entry(2, "BUF"), Map.entry(3, "CHI"),
            Map.entry(4, "CIN"), Map.entry(5, "CLE"), Map.entry(6, "DAL"),
            Map.entry(7, "DEN"), Map.entry(8, "DET"), Map.entry(9, "GB"),
            Map.entry(10, "TEN"), Map.entry(11, "IND"), Map.entry(12, "KC"),
            Map.entry(13, "LV"), Map.entry(14, "LAR"), Map.entry(15, "MIA"),
            Map.entry(16, "MIN"), Map.entry(17, "NE"), Map.entry(18, "NO"),
            Map.entry(19, "NYG"), Map.entry(20, "NYJ"), Map.entry(21, "PHI"),
            Map.entry(22, "ARI"), Map.entry(23, "PIT"), Map.entry(24, "LAC"),
            Map.entry(25, "SF"), Map.entry(26, "SEA"), Map.entry(27, "TB"),
            Map.entry(28, "WAS"), Map.entry(29, "CAR"), Map.entry(30, "JAX"),
            Map.entry(33, "BAL"), Map.entry(34, "HOU"));

    /** ESPN position ID → standard abbreviation */
    private static final Map<Integer, String> POSITION_MAP = Map.of(
            1, "QB", 2, "RB", 3, "WR", 4, "TE", 5, "K", 16, "DEF");

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
        // Try the requested year first, fall back to the previous year if 404
        List<PlayerRanking> result = tryFetchForSeason(year);
        if (result != null)
            return result;

        log.warn("ESPN: {} season data not available, trying {}", year, year - 1);
        result = tryFetchForSeason(year - 1);
        if (result != null)
            return result;

        throw new ScrapingException(SOURCE_ID,
                "ESPN Fantasy API returned no data for " + year + " or " + (year - 1) +
                        ". The API may be unavailable during the off-season.");
    }

    // ═══════════════════════════════════════════════════════════════
    // FETCHING
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> tryFetchForSeason(int season) throws ScrapingException {
        String url = String.format(API_TEMPLATE, season);
        String filter = String.format(FANTASY_FILTER_TEMPLATE, PLAYER_LIMIT);

        log.info("ESPN: fetching from {} (season {})", url, season);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .header("X-Fantasy-Filter", filter)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                log.info("ESPN: season {} returned 404", season);
                return null; // Signal to try another season
            }

            if (response.statusCode() != 200) {
                throw new ScrapingException(SOURCE_ID,
                        "ESPN API returned HTTP " + response.statusCode());
            }

            return parseResponse(response.body());

        } catch (ScrapingException e) {
            throw e;
        } catch (Exception e) {
            throw new ScrapingException(SOURCE_ID,
                    "Failed to fetch from ESPN API: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PARSING
    // ═══════════════════════════════════════════════════════════════

    private List<PlayerRanking> parseResponse(String body) throws ScrapingException {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode playersNode = root.path("players");

            if (!playersNode.isArray() || playersNode.isEmpty()) {
                log.warn("ESPN: 'players' array not found or empty in API response");
                throw new ScrapingException(SOURCE_ID,
                        "ESPN API response did not contain a 'players' array");
            }

            List<PlayerRanking> results = new ArrayList<>();
            int fallbackRank = 1;

            for (JsonNode entry : playersNode) {
                JsonNode playerNode = entry.path("player");
                if (playerNode.isMissingNode())
                    continue;

                String fullName = playerNode.path("fullName").asText("");
                if (fullName.isBlank())
                    continue;

                // Position
                int positionId = playerNode.path("defaultPositionId").asInt(-1);
                String position = POSITION_MAP.getOrDefault(positionId, "");
                if (position.isEmpty())
                    continue;

                // Team
                int teamId = playerNode.path("proTeamId").asInt(0);
                String team = TEAM_MAP.getOrDefault(teamId, "FA");

                // Draft rank — try draftRanksByRankType.STANDARD.rank first
                int rank = fallbackRank;
                JsonNode draftRanks = playerNode.path("draftRanksByRankType").path("STANDARD");
                if (!draftRanks.isMissingNode() && draftRanks.has("rank")) {
                    rank = draftRanks.path("rank").asInt(fallbackRank);
                }

                PlayerRanking ranking = new PlayerRanking(fullName, position, team);
                ranking.addSourceRanking(SOURCE_ID, rank);
                results.add(ranking);
                fallbackRank++;
            }

            // Sort by rank and re-number to ensure sequential ordering
            results.sort(Comparator.comparingInt(p -> p.getRankings().get(SOURCE_ID)));
            for (int i = 0; i < results.size(); i++) {
                results.get(i).addSourceRanking(SOURCE_ID, i + 1);
            }

            log.info("ESPN: parsed {} players", results.size());
            return results;

        } catch (ScrapingException e) {
            throw e;
        } catch (Exception e) {
            throw new ScrapingException(SOURCE_ID,
                    "Failed to parse ESPN API response: " + e.getMessage(), e);
        }
    }
}
