package com.FantasyPicks.fantasy_picks.normalization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalization Layer for player and team data.
 *
 * Different data sources format player names and team abbreviations inconsistently.
 * For example:
 *   - "Patrick Mahomes II" (ESPN) vs "Patrick Mahomes" (FantasyPros)
 *   - "Amon-Ra St. Brown" vs "Amon-Ra St Brown"
 *   - "D.J. Moore" vs "DJ Moore"
 *   - "KAN" vs "KC" vs "Kansas City Chiefs"
 *
 * This class provides deterministic normalization so that the same player always
 * produces the same deduplication key regardless of which source provided the data.
 */
@Component
public class PlayerNormalizer {

    private static final Logger log = LoggerFactory.getLogger(PlayerNormalizer.class);

    // ═══════════════════════════════════════════════════════════════
    // NAME NORMALIZATION
    // ═══════════════════════════════════════════════════════════════

    /** Suffixes to strip (case-insensitive matching) */
    private static final Pattern SUFFIX_PATTERN = Pattern.compile(
            "\\s+(jr\\.?|sr\\.?|ii|iii|iv|v)$", Pattern.CASE_INSENSITIVE);

    /** Periods used in initials: "D.J." → "DJ", "T.J." → "TJ" */
    private static final Pattern PERIOD_PATTERN = Pattern.compile("\\.");

    /** Apostrophes and similar characters */
    private static final Pattern APOSTROPHE_PATTERN = Pattern.compile("[''`]");

    /** Multiple consecutive spaces */
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s{2,}");

    /** Common name variants → canonical form (lowercase → lowercase) */
    private static final Map<String, String> NAME_ALIASES = Map.ofEntries(
            // First name variants
            Map.entry("mitch trubisky", "mitchell trubisky"),
            Map.entry("gabe davis", "gabriel davis"),
            Map.entry("scotty miller", "scott miller"),
            Map.entry("rob gronkowski", "robert gronkowski"),
            Map.entry("mike williams", "michael williams"),
            Map.entry("ken walker iii", "kenneth walker"),
            Map.entry("kenneth walker iii", "kenneth walker"),
            Map.entry("ken walker", "kenneth walker"),

            // Defense/Special teams naming
            Map.entry("chiefs d/st", "kansas city defense"),
            Map.entry("chiefs defense", "kansas city defense"),
            Map.entry("49ers d/st", "san francisco defense"),
            Map.entry("49ers defense", "san francisco defense")
    );

    /**
     * Normalize a player name for use in deduplication keys.
     *
     * Steps:
     *   1. Lowercase and trim
     *   2. Normalize Unicode (accented chars → ASCII equivalents)
     *   3. Remove suffixes (Jr., Sr., II, III, IV, V)
     *   4. Remove periods (D.J. → DJ)
     *   5. Remove apostrophes (D'Andre → DAndre)
     *   6. Collapse multiple spaces
     *   7. Apply known nickname → canonical name mappings
     *
     * @param rawName the player name as returned by a scraper
     * @return a normalized, deterministic string for matching
     */
    public String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "";

        String name = rawName.trim().toLowerCase();

        // Unicode normalization: é → e, ñ → n, etc.
        name = stripAccents(name);

        // Remove suffixes: "Jr.", "Sr.", "II", "III", "IV", "V"
        name = SUFFIX_PATTERN.matcher(name).replaceAll("");

        // Remove periods: "D.J." → "DJ", "St." → "St"
        name = PERIOD_PATTERN.matcher(name).replaceAll("");

        // Remove apostrophes: "D'Andre" → "DAndre", "O'Brien" → "OBrien"
        name = APOSTROPHE_PATTERN.matcher(name).replaceAll("");

        // Collapse spaces
        name = MULTI_SPACE_PATTERN.matcher(name).replaceAll(" ").trim();

        // Apply known aliases
        String alias = NAME_ALIASES.get(name);
        if (alias != null) {
            name = alias;
        }

        return name;
    }

    // ═══════════════════════════════════════════════════════════════
    // TEAM NORMALIZATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Comprehensive mapping of every known team name variant → standard 2-3 letter abbreviation.
     * Covers: full names, city names, mascot names, alternate abbreviations from various APIs.
     */
    private static final Map<String, String> TEAM_ALIASES = Map.ofEntries(
            // Arizona Cardinals
            Map.entry("ari", "ARI"), Map.entry("arizona", "ARI"), Map.entry("cardinals", "ARI"),
            Map.entry("arizona cardinals", "ARI"),

            // Atlanta Falcons
            Map.entry("atl", "ATL"), Map.entry("atlanta", "ATL"), Map.entry("falcons", "ATL"),
            Map.entry("atlanta falcons", "ATL"),

            // Baltimore Ravens
            Map.entry("bal", "BAL"), Map.entry("blt", "BAL"), Map.entry("baltimore", "BAL"),
            Map.entry("ravens", "BAL"), Map.entry("baltimore ravens", "BAL"),

            // Buffalo Bills
            Map.entry("buf", "BUF"), Map.entry("buffalo", "BUF"), Map.entry("bills", "BUF"),
            Map.entry("buffalo bills", "BUF"),

            // Carolina Panthers
            Map.entry("car", "CAR"), Map.entry("carolina", "CAR"), Map.entry("panthers", "CAR"),
            Map.entry("carolina panthers", "CAR"),

            // Chicago Bears
            Map.entry("chi", "CHI"), Map.entry("chicago", "CHI"), Map.entry("bears", "CHI"),
            Map.entry("chicago bears", "CHI"),

            // Cincinnati Bengals
            Map.entry("cin", "CIN"), Map.entry("cincinnati", "CIN"), Map.entry("bengals", "CIN"),
            Map.entry("cincinnati bengals", "CIN"),

            // Cleveland Browns
            Map.entry("cle", "CLE"), Map.entry("cleveland", "CLE"), Map.entry("browns", "CLE"),
            Map.entry("cleveland browns", "CLE"),

            // Dallas Cowboys
            Map.entry("dal", "DAL"), Map.entry("dallas", "DAL"), Map.entry("cowboys", "DAL"),
            Map.entry("dallas cowboys", "DAL"),

            // Denver Broncos
            Map.entry("den", "DEN"), Map.entry("denver", "DEN"), Map.entry("broncos", "DEN"),
            Map.entry("denver broncos", "DEN"),

            // Detroit Lions
            Map.entry("det", "DET"), Map.entry("detroit", "DET"), Map.entry("lions", "DET"),
            Map.entry("detroit lions", "DET"),

            // Green Bay Packers
            Map.entry("gb", "GB"), Map.entry("gnb", "GB"), Map.entry("green bay", "GB"),
            Map.entry("packers", "GB"), Map.entry("green bay packers", "GB"),

            // Houston Texans
            Map.entry("hou", "HOU"), Map.entry("houston", "HOU"), Map.entry("texans", "HOU"),
            Map.entry("houston texans", "HOU"),

            // Indianapolis Colts
            Map.entry("ind", "IND"), Map.entry("indianapolis", "IND"), Map.entry("colts", "IND"),
            Map.entry("indianapolis colts", "IND"),

            // Jacksonville Jaguars
            Map.entry("jax", "JAX"), Map.entry("jac", "JAX"), Map.entry("jacksonville", "JAX"),
            Map.entry("jaguars", "JAX"), Map.entry("jacksonville jaguars", "JAX"),

            // Kansas City Chiefs
            Map.entry("kc", "KC"), Map.entry("kan", "KC"), Map.entry("kansas city", "KC"),
            Map.entry("chiefs", "KC"), Map.entry("kansas city chiefs", "KC"),

            // Las Vegas Raiders
            Map.entry("lv", "LV"), Map.entry("lvr", "LV"), Map.entry("las vegas", "LV"),
            Map.entry("raiders", "LV"), Map.entry("las vegas raiders", "LV"),
            Map.entry("oak", "LV"), Map.entry("oakland", "LV"),

            // Los Angeles Chargers
            Map.entry("lac", "LAC"), Map.entry("chargers", "LAC"),
            Map.entry("los angeles chargers", "LAC"), Map.entry("la chargers", "LAC"),
            Map.entry("sdg", "LAC"), Map.entry("sd", "LAC"),

            // Los Angeles Rams
            Map.entry("lar", "LAR"), Map.entry("la", "LAR"), Map.entry("rams", "LAR"),
            Map.entry("los angeles rams", "LAR"), Map.entry("la rams", "LAR"),
            Map.entry("stl", "LAR"),

            // Miami Dolphins
            Map.entry("mia", "MIA"), Map.entry("miami", "MIA"), Map.entry("dolphins", "MIA"),
            Map.entry("miami dolphins", "MIA"),

            // Minnesota Vikings
            Map.entry("min", "MIN"), Map.entry("minnesota", "MIN"), Map.entry("vikings", "MIN"),
            Map.entry("minnesota vikings", "MIN"),

            // New England Patriots
            Map.entry("ne", "NE"), Map.entry("nwe", "NE"), Map.entry("new england", "NE"),
            Map.entry("patriots", "NE"), Map.entry("new england patriots", "NE"),
            Map.entry("pats", "NE"),

            // New Orleans Saints
            Map.entry("no", "NO"), Map.entry("nor", "NO"), Map.entry("new orleans", "NO"),
            Map.entry("saints", "NO"), Map.entry("new orleans saints", "NO"),

            // New York Giants
            Map.entry("nyg", "NYG"), Map.entry("giants", "NYG"),
            Map.entry("new york giants", "NYG"), Map.entry("ny giants", "NYG"),

            // New York Jets
            Map.entry("nyj", "NYJ"), Map.entry("jets", "NYJ"),
            Map.entry("new york jets", "NYJ"), Map.entry("ny jets", "NYJ"),

            // Philadelphia Eagles
            Map.entry("phi", "PHI"), Map.entry("philadelphia", "PHI"), Map.entry("eagles", "PHI"),
            Map.entry("philadelphia eagles", "PHI"),

            // Pittsburgh Steelers
            Map.entry("pit", "PIT"), Map.entry("pittsburgh", "PIT"), Map.entry("steelers", "PIT"),
            Map.entry("pittsburgh steelers", "PIT"),

            // San Francisco 49ers
            Map.entry("sf", "SF"), Map.entry("sfo", "SF"), Map.entry("san francisco", "SF"),
            Map.entry("49ers", "SF"), Map.entry("san francisco 49ers", "SF"),
            Map.entry("niners", "SF"),

            // Seattle Seahawks
            Map.entry("sea", "SEA"), Map.entry("seattle", "SEA"), Map.entry("seahawks", "SEA"),
            Map.entry("seattle seahawks", "SEA"),

            // Tampa Bay Buccaneers
            Map.entry("tb", "TB"), Map.entry("tam", "TB"), Map.entry("tampa bay", "TB"),
            Map.entry("buccaneers", "TB"), Map.entry("bucs", "TB"),
            Map.entry("tampa bay buccaneers", "TB"),

            // Tennessee Titans
            Map.entry("ten", "TEN"), Map.entry("tennessee", "TEN"), Map.entry("titans", "TEN"),
            Map.entry("tennessee titans", "TEN"),

            // Washington Commanders
            Map.entry("was", "WAS"), Map.entry("wsh", "WAS"), Map.entry("washington", "WAS"),
            Map.entry("commanders", "WAS"), Map.entry("washington commanders", "WAS"),

            // Free Agent / Unknown
            Map.entry("fa", "FA"), Map.entry("free agent", "FA")
    );

    /**
     * Normalize a team name or abbreviation to its standard form.
     *
     * @param rawTeam the team string from any source (e.g. "KAN", "KC", "Chiefs")
     * @return the canonical uppercase abbreviation (e.g. "KC"), or the original
     *         uppercased value if no mapping is found
     */
    public String normalizeTeam(String rawTeam) {
        if (rawTeam == null || rawTeam.isBlank()) return "FA";

        String key = rawTeam.trim().toLowerCase();
        String mapped = TEAM_ALIASES.get(key);
        return mapped != null ? mapped : rawTeam.trim().toUpperCase();
    }

    // ═══════════════════════════════════════════════════════════════
    // COMPOSITE KEY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Build a normalized deduplication key for a player.
     * This is the single method that PlayerService should use for merging.
     *
     * @param name     raw player name
     * @param team     raw team abbreviation/name
     * @return a normalized key like "patrick mahomes|kc"
     */
    public String buildNormalizedKey(String name, String team) {
        return normalizeName(name) + "|" + normalizeTeam(team).toLowerCase();
    }

    // ═══════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Strip Unicode accent marks / diacritics.
     * "José" → "Jose", "Hernández" → "Hernandez"
     */
    private String stripAccents(String input) {
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
