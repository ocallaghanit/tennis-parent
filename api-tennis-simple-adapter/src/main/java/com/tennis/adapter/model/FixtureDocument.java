package com.tennis.adapter.model;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Stores fixture/match information
 */
@Document(collection = "fixtures")
@CompoundIndex(name = "tournament_date_idx", def = "{'tournamentKey': 1, 'eventDate': 1}")
public class FixtureDocument extends BaseDocument {

    @Indexed(unique = true)
    private String eventKey;

    @Indexed
    private String tournamentKey;

    @Indexed
    private LocalDate eventDate;

    @Indexed
    private String firstPlayerKey;
    private String firstPlayerName;

    @Indexed
    private String secondPlayerKey;
    private String secondPlayerName;

    private String status;
    private String winner;
    private String score;

    public FixtureDocument() {
        super();
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getTournamentKey() {
        return tournamentKey;
    }

    public void setTournamentKey(String tournamentKey) {
        this.tournamentKey = tournamentKey;
    }

    public LocalDate getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDate eventDate) {
        this.eventDate = eventDate;
    }

    public String getFirstPlayerKey() {
        return firstPlayerKey;
    }

    public void setFirstPlayerKey(String firstPlayerKey) {
        this.firstPlayerKey = firstPlayerKey;
    }

    public String getFirstPlayerName() {
        return firstPlayerName;
    }

    public void setFirstPlayerName(String firstPlayerName) {
        this.firstPlayerName = firstPlayerName;
    }

    public String getSecondPlayerKey() {
        return secondPlayerKey;
    }

    public void setSecondPlayerKey(String secondPlayerKey) {
        this.secondPlayerKey = secondPlayerKey;
    }

    public String getSecondPlayerName() {
        return secondPlayerName;
    }

    public void setSecondPlayerName(String secondPlayerName) {
        this.secondPlayerName = secondPlayerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public String getScore() {
        return score;
    }

    public void setScore(String score) {
        this.score = score;
    }
}

