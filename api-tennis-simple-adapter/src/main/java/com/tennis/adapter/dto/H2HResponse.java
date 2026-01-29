package com.tennis.adapter.dto;

import java.util.List;

/**
 * Head-to-head response with summary statistics.
 */
public record H2HResponse(
        String player1Key,
        String player1Name,
        int player1Wins,
        String player2Key,
        String player2Name,
        int player2Wins,
        int totalMatches,
        List<FixtureResponse> matches
) {
    public static H2HResponse create(
            String player1Key, String player1Name,
            String player2Key, String player2Name,
            List<FixtureResponse> matches
    ) {
        int p1Wins = 0;
        int p2Wins = 0;
        
        for (FixtureResponse match : matches) {
            if (player1Key.equals(match.winner())) {
                p1Wins++;
            } else if (player2Key.equals(match.winner())) {
                p2Wins++;
            }
        }
        
        return new H2HResponse(
                player1Key, player1Name, p1Wins,
                player2Key, player2Name, p2Wins,
                matches.size(), matches
        );
    }
}

