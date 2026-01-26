package com.tennis.adapter.model;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Stores odds/betting data for matches
 */
@Document(collection = "odds")
public class OddsDocument extends BaseDocument {

    @Indexed
    private String matchKey;

    @Indexed
    private LocalDate eventDate;

    private String tournamentKey;

    public OddsDocument() {
        super();
    }

    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getTournamentKey() {
        return tournamentKey;
    }

    public void setTournamentKey(String tournamentKey) {
        this.tournamentKey = tournamentKey;
    }
}

