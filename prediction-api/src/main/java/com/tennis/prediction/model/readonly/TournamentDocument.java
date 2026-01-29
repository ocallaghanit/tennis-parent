package com.tennis.prediction.model.readonly;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Read-only model for tournaments from the adapter service.
 * This service cannot write to this collection.
 */
@org.springframework.data.mongodb.core.mapping.Document(collection = "tournaments")
public class TournamentDocument {

    @Id
    private String id;

    private String tournamentKey;
    private String tournamentName;
    private String eventTypeKey;
    private String surface;
    private String country;

    private Document raw;
    private Instant fetchedAt;
    private Instant updatedAt;

    // Getters only (read-only)
    public String getId() { return id; }
    public String getTournamentKey() { return tournamentKey; }
    public String getTournamentName() { return tournamentName; }
    public String getEventTypeKey() { return eventTypeKey; }
    public String getSurface() { return surface; }
    public String getCountry() { return country; }
    public Document getRaw() { return raw; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

