package com.FantasyPicks.fantasy_picks.model;

import java.time.Instant;
import java.util.List;

/**
 * API response wrapper sent to the frontend.
 * Contains the list of players and a timestamp for cache freshness.
 */
public class PlayerApiResponse {

    private List<PlayerRanking> players;
    private String lastUpdated;
    private java.util.Map<String, String> sourceStatuses = new java.util.HashMap<>();

    public PlayerApiResponse() {
    }

    public PlayerApiResponse(List<PlayerRanking> players) {
        this.players = players;
        this.lastUpdated = Instant.now().toString();
    }

    // ── Getters & Setters ─────────────────────────────────

    public List<PlayerRanking> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerRanking> players) {
        this.players = players;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public java.util.Map<String, String> getSourceStatuses() {
        return sourceStatuses;
    }

    public void setSourceStatuses(java.util.Map<String, String> sourceStatuses) {
        this.sourceStatuses = sourceStatuses;
    }
}
