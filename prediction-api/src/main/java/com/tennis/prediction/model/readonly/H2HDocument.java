package com.tennis.prediction.model.readonly;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only model for head-to-head data from the adapter service.
 * This service cannot write to this collection.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "h2h")
public class H2HDocument {

    @Id
    private String id;

    private String playerKey1;
    private String playerKey2;
    private int player1Wins;
    private int player2Wins;
    private List<H2HMatch> matches;

    private Document raw;
    private Instant fetchedAt;
    private Instant updatedAt;

    // Getters only (read-only)
    public String getId() { return id; }
    public String getPlayerKey1() { return playerKey1; }
    public String getPlayerKey2() { return playerKey2; }
    public int getPlayer1Wins() { return player1Wins; }
    public int getPlayer2Wins() { return player2Wins; }
    public List<H2HMatch> getMatches() { return matches; }
    public Document getRaw() { return raw; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Get wins for a specific player
     */
    public int getWinsFor(String playerKey) {
        if (playerKey == null) return 0;
        if (playerKey.equals(playerKey1)) return player1Wins;
        if (playerKey.equals(playerKey2)) return player2Wins;
        return 0;
    }

    public static class H2HMatch {
        private String eventKey;
        private LocalDate eventDate;
        private String tournamentName;
        private String score;
        private String winnerKey;

        public String getEventKey() { return eventKey; }
        public LocalDate getEventDate() { return eventDate; }
        public String getTournamentName() { return tournamentName; }
        public String getScore() { return score; }
        public String getWinnerKey() { return winnerKey; }
    }
}

