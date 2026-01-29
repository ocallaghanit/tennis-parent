package com.tennis.prediction.model.readonly;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-only model for odds from the adapter service.
 * This service cannot write to this collection.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "odds")
public class OddsDocument {

    @Id
    private String id;

    private String matchKey;
    private LocalDate matchDate;

    private Document raw;
    private Instant fetchedAt;
    private Instant updatedAt;

    // Getters only (read-only)
    public String getId() { return id; }
    public String getMatchKey() { return matchKey; }
    public LocalDate getMatchDate() { return matchDate; }
    public Document getRaw() { return raw; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Extract home odds (player 1) from bet365 or first available bookmaker
     */
    public Double getHomeOdds() {
        return extractOdds("Home");
    }

    /**
     * Extract away odds (player 2) from bet365 or first available bookmaker
     */
    public Double getAwayOdds() {
        return extractOdds("Away");
    }

    private Double extractOdds(String side) {
        if (raw == null) return null;
        Object homeAwayObj = raw.get("Home/Away");
        if (!(homeAwayObj instanceof Document)) return null;
        
        Document homeAway = (Document) homeAwayObj;
        Object sideObj = homeAway.get(side);
        if (!(sideObj instanceof Document)) return null;
        
        Document sideDoc = (Document) sideObj;
        // Try bet365 first
        String odds = sideDoc.getString("bet365");
        if (odds == null) {
            // Fallback to any available
            odds = sideDoc.values().stream()
                    .filter(v -> v instanceof String)
                    .map(v -> (String) v)
                    .findFirst().orElse(null);
        }
        
        if (odds != null) {
            try {
                return Double.parseDouble(odds);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

