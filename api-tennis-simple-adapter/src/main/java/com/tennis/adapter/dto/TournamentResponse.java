package com.tennis.adapter.dto;

import com.tennis.adapter.model.TournamentDocument;

import java.time.Instant;

/**
 * Clean DTO for tournament data, hiding internal MongoDB details.
 */
public record TournamentResponse(
        String tournamentKey,
        String tournamentName,
        String eventTypeKey,
        String surface,
        String country,
        Instant updatedAt
) {
    public static TournamentResponse from(TournamentDocument doc) {
        return new TournamentResponse(
                doc.getTournamentKey(),
                doc.getTournamentName(),
                doc.getEventTypeKey(),
                doc.getSurface(),
                doc.getCountry(),
                doc.getUpdatedAt()
        );
    }
}

