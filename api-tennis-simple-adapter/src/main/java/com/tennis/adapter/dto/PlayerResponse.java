package com.tennis.adapter.dto;

import com.tennis.adapter.model.PlayerDocument;

import java.time.Instant;

/**
 * Clean DTO for player data, hiding internal MongoDB details.
 */
public record PlayerResponse(
        String playerKey,
        String playerName,
        String country,
        String hand,
        Integer currentRank,
        Instant updatedAt
) {
    public static PlayerResponse from(PlayerDocument doc) {
        return new PlayerResponse(
                doc.getPlayerKey(),
                doc.getPlayerName(),
                doc.getCountry(),
                doc.getHand(),
                doc.getCurrentRank(),
                doc.getUpdatedAt()
        );
    }
}

