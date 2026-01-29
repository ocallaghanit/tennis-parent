package com.tennis.adapter.dto;

import com.tennis.adapter.model.FixtureDocument;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Clean DTO for fixture data, hiding internal MongoDB details.
 */
public record FixtureResponse(
        String eventKey,
        String tournamentKey,
        String eventTypeKey,
        LocalDate eventDate,
        String player1Key,
        String player1Name,
        String player2Key,
        String player2Name,
        String status,
        String winner,
        String score,
        Instant updatedAt
) {
    public static FixtureResponse from(FixtureDocument doc) {
        return new FixtureResponse(
                doc.getEventKey(),
                doc.getTournamentKey(),
                doc.getEventTypeKey(),
                doc.getEventDate(),
                doc.getFirstPlayerKey(),
                doc.getFirstPlayerName(),
                doc.getSecondPlayerKey(),
                doc.getSecondPlayerName(),
                doc.getStatus(),
                doc.getWinner(),
                doc.getScore(),
                doc.getUpdatedAt()
        );
    }
}

