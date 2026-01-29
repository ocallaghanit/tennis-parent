package com.tennis.adapter.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Stores head-to-head data between two players.
 * The ID is a composite of both player keys (sorted alphabetically to ensure consistency).
 */
@Document(collection = "h2h")
@CompoundIndex(name = "players_idx", def = "{'playerKey1': 1, 'playerKey2': 1}", unique = true)
public class H2HDocument extends BaseDocument {

    @Indexed
    private String playerKey1;
    
    @Indexed
    private String playerKey2;
    
    private String player1Name;
    private String player2Name;
    
    // Total H2H wins for each player (from all historical matches)
    private int player1Wins;
    private int player2Wins;
    
    // When the H2H data was last fetched from API
    private Instant lastFetched;

    public H2HDocument() {
        super();
    }

    /**
     * Create a consistent ID from two player keys (sorted alphabetically)
     */
    public static String createId(String playerKeyA, String playerKeyB) {
        if (playerKeyA.compareTo(playerKeyB) < 0) {
            return playerKeyA + "_" + playerKeyB;
        }
        return playerKeyB + "_" + playerKeyA;
    }

    public String getPlayerKey1() {
        return playerKey1;
    }

    public void setPlayerKey1(String playerKey1) {
        this.playerKey1 = playerKey1;
    }

    public String getPlayerKey2() {
        return playerKey2;
    }

    public void setPlayerKey2(String playerKey2) {
        this.playerKey2 = playerKey2;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public void setPlayer1Name(String player1Name) {
        this.player1Name = player1Name;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public void setPlayer2Name(String player2Name) {
        this.player2Name = player2Name;
    }

    public int getPlayer1Wins() {
        return player1Wins;
    }

    public void setPlayer1Wins(int player1Wins) {
        this.player1Wins = player1Wins;
    }

    public int getPlayer2Wins() {
        return player2Wins;
    }

    public void setPlayer2Wins(int player2Wins) {
        this.player2Wins = player2Wins;
    }

    public Instant getLastFetched() {
        return lastFetched;
    }

    public void setLastFetched(Instant lastFetched) {
        this.lastFetched = lastFetched;
    }
}

