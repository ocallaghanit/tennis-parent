package com.tennis.prediction.model;

import java.time.LocalDate;

/**
 * Embedded document representing a single rating change event.
 * Stored in OwlRatingDocument.recentChanges list.
 */
public class RatingChange {

    private String matchKey;
    private LocalDate matchDate;
    
    private String opponentKey;
    private String opponentName;
    private double opponentRatingBefore;
    
    private boolean won;
    private String score;
    
    // Odds-based calculation details
    private Double odds;                    // Winner's odds for this match
    private double expectedWinProb;         // Implied probability from odds
    private double dominanceMultiplier;     // Score-based multiplier
    private double tournamentMultiplier;    // Tournament importance multiplier
    
    private double pointsChange;            // +/- points gained/lost
    private double ratingBefore;
    private double ratingAfter;
    
    private String tournamentName;

    public RatingChange() {}

    // ============ BUILDER PATTERN ============

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final RatingChange rc = new RatingChange();

        public Builder matchKey(String matchKey) { rc.matchKey = matchKey; return this; }
        public Builder matchDate(LocalDate matchDate) { rc.matchDate = matchDate; return this; }
        public Builder opponentKey(String opponentKey) { rc.opponentKey = opponentKey; return this; }
        public Builder opponentName(String opponentName) { rc.opponentName = opponentName; return this; }
        public Builder opponentRatingBefore(double rating) { rc.opponentRatingBefore = rating; return this; }
        public Builder won(boolean won) { rc.won = won; return this; }
        public Builder score(String score) { rc.score = score; return this; }
        public Builder odds(Double odds) { rc.odds = odds; return this; }
        public Builder expectedWinProb(double prob) { rc.expectedWinProb = prob; return this; }
        public Builder dominanceMultiplier(double mult) { rc.dominanceMultiplier = mult; return this; }
        public Builder tournamentMultiplier(double mult) { rc.tournamentMultiplier = mult; return this; }
        public Builder pointsChange(double points) { rc.pointsChange = points; return this; }
        public Builder ratingBefore(double rating) { rc.ratingBefore = rating; return this; }
        public Builder ratingAfter(double rating) { rc.ratingAfter = rating; return this; }
        public Builder tournamentName(String name) { rc.tournamentName = name; return this; }

        public RatingChange build() { return rc; }
    }

    // ============ HELPER METHODS ============

    public String getPointsChangeFormatted() {
        return String.format("%+.1f", pointsChange);
    }

    public String getExpectedWinProbFormatted() {
        return String.format("%.0f%%", expectedWinProb * 100);
    }

    public String getOddsFormatted() {
        return odds != null ? String.format("%.2f", odds) : "-";
    }

    public String getResult() {
        return won ? "W" : "L";
    }

    // ============ GETTERS AND SETTERS ============

    public String getMatchKey() { return matchKey; }
    public void setMatchKey(String matchKey) { this.matchKey = matchKey; }

    public LocalDate getMatchDate() { return matchDate; }
    public void setMatchDate(LocalDate matchDate) { this.matchDate = matchDate; }

    public String getOpponentKey() { return opponentKey; }
    public void setOpponentKey(String opponentKey) { this.opponentKey = opponentKey; }

    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }

    public double getOpponentRatingBefore() { return opponentRatingBefore; }
    public void setOpponentRatingBefore(double opponentRatingBefore) { this.opponentRatingBefore = opponentRatingBefore; }

    public boolean isWon() { return won; }
    public void setWon(boolean won) { this.won = won; }

    public String getScore() { return score; }
    public void setScore(String score) { this.score = score; }

    public Double getOdds() { return odds; }
    public void setOdds(Double odds) { this.odds = odds; }

    public double getExpectedWinProb() { return expectedWinProb; }
    public void setExpectedWinProb(double expectedWinProb) { this.expectedWinProb = expectedWinProb; }

    public double getDominanceMultiplier() { return dominanceMultiplier; }
    public void setDominanceMultiplier(double dominanceMultiplier) { this.dominanceMultiplier = dominanceMultiplier; }

    public double getTournamentMultiplier() { return tournamentMultiplier; }
    public void setTournamentMultiplier(double tournamentMultiplier) { this.tournamentMultiplier = tournamentMultiplier; }

    public double getPointsChange() { return pointsChange; }
    public void setPointsChange(double pointsChange) { this.pointsChange = pointsChange; }

    public double getRatingBefore() { return ratingBefore; }
    public void setRatingBefore(double ratingBefore) { this.ratingBefore = ratingBefore; }

    public double getRatingAfter() { return ratingAfter; }
    public void setRatingAfter(double ratingAfter) { this.ratingAfter = ratingAfter; }

    public String getTournamentName() { return tournamentName; }
    public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
}

