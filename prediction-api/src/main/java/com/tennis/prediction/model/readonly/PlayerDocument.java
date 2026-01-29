package com.tennis.prediction.model.readonly;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Read-only model for players from the adapter service.
 * This service cannot write to this collection.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "players")
public class PlayerDocument {

    @Id
    private String id;

    private String playerKey;
    private String playerName;
    private String country;
    private String hand;
    private Integer currentRank;
    private String playerImageUrl;

    private Document raw;
    private Instant fetchedAt;
    private Instant updatedAt;

    // Getters only (read-only)
    public String getId() { return id; }
    public String getPlayerKey() { return playerKey; }
    public String getPlayerName() { return playerName; }
    public String getCountry() { return country; }
    public String getHand() { return hand; }
    public Integer getCurrentRank() { return currentRank; }
    public String getPlayerImageUrl() { return playerImageUrl; }
    public Document getRaw() { return raw; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

