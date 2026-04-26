package com.FantasyPicks.fantasy_picks.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single player with their ranking data from multiple sources.
 * This is the DTO sent to the frontend as part of the API response.
 */
public class PlayerRanking {

    private String name;
    private String position;
    private String team;
    private int overallRank;

    /**
     * Per-source rankings. Key = source ID (e.g. "fantasypros", "espn"),
     * Value = the player's rank from that source.
     */
    private Map<String, Integer> rankings;

    public PlayerRanking() {
        this.rankings = new HashMap<>();
    }

    public PlayerRanking(String name, String position, String team) {
        this.name = name;
        this.position = position;
        this.team = team;
        this.rankings = new HashMap<>();
    }

    // ── Getters & Setters ─────────────────────────────────

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public int getOverallRank() {
        return overallRank;
    }

    public void setOverallRank(int overallRank) {
        this.overallRank = overallRank;
    }

    public Map<String, Integer> getRankings() {
        return rankings;
    }

    public void setRankings(Map<String, Integer> rankings) {
        this.rankings = rankings;
    }

    /**
     * Add or update a ranking from a specific source.
     */
    public void addSourceRanking(String sourceId, int rank) {
        this.rankings.put(sourceId, rank);
    }

    @Override
    public String toString() {
        return "PlayerRanking{" +
                "name='" + name + '\'' +
                ", position='" + position + '\'' +
                ", team='" + team + '\'' +
                ", overallRank=" + overallRank +
                ", rankings=" + rankings +
                '}';
    }
}
