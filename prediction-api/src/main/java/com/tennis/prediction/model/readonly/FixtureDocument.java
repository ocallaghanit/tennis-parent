package com.tennis.prediction.model.readonly;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-only model for fixtures from the adapter service.
 * This service cannot write to this collection.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "fixtures")
public class FixtureDocument {

    @Id
    private String id;

    private String eventKey;
    private String tournamentKey;
    private String eventTypeKey;
    private LocalDate eventDate;

    private String firstPlayerKey;
    private String firstPlayerName;
    private String secondPlayerKey;
    private String secondPlayerName;

    private String status;
    private String winner;
    private String score;

    private Document raw;
    private Instant fetchedAt;
    private Instant updatedAt;

    // Getters only (read-only)
    public String getId() { return id; }
    public String getEventKey() { return eventKey; }
    public String getTournamentKey() { return tournamentKey; }
    public String getEventTypeKey() { return eventTypeKey; }
    public LocalDate getEventDate() { return eventDate; }
    public String getFirstPlayerKey() { return firstPlayerKey; }
    public String getFirstPlayerName() { return firstPlayerName; }
    public String getSecondPlayerKey() { return secondPlayerKey; }
    public String getSecondPlayerName() { return secondPlayerName; }
    public String getStatus() { return status; }
    public String getWinner() { return winner; }
    public String getScore() { return score; }
    public Document getRaw() { return raw; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Check if a player key is the winner
     */
    public boolean isWinner(String playerKey) {
        if (winner == null || playerKey == null) return false;
        return playerKey.equals(winner) ||
               ("First Player".equals(winner) && playerKey.equals(firstPlayerKey)) ||
               ("Second Player".equals(winner) && playerKey.equals(secondPlayerKey));
    }

    /**
     * Check if match is finished
     */
    public boolean isFinished() {
        return "Finished".equalsIgnoreCase(status);
    }
}

