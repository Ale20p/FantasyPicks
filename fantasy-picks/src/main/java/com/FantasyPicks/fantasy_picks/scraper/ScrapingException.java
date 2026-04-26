package com.FantasyPicks.fantasy_picks.scraper;

/**
 * Custom exception for scraping failures.
 * Wraps underlying I/O, parsing, or network errors with context about which source failed.
 */
public class ScrapingException extends Exception {

    private final String sourceId;

    public ScrapingException(String sourceId, String message) {
        super("[" + sourceId + "] " + message);
        this.sourceId = sourceId;
    }

    public ScrapingException(String sourceId, String message, Throwable cause) {
        super("[" + sourceId + "] " + message, cause);
        this.sourceId = sourceId;
    }

    public String getSourceId() {
        return sourceId;
    }
}
