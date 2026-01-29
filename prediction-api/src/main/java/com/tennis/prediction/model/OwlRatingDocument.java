package com.tennis.prediction.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * OWL (Odds-Weighted Ladder) Rating Document.
 * 
 * A dynamic rating system that uses betting odds to determine expected outcomes,
 * rewarding upset wins more heavily and penalizing favorites who lose.
 */
@Document(collection = "owl_ratings")
public class OwlRatingDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String playerKey;
    
    private String playerName;

    private double rating;           // Current OWL rating
    private Integer atpRank;         // Current ATP rank for reference
    
    @Indexed
    private int owlRank;             // Rank in OWL system (calculated)

    private int matchesPlayed;
    private int wins;
    private int losses;

    private double peakRating;
    private LocalDate peakDate;
    
    // Rolling statistics
    private double last10WinRate;    // Win rate in last 10 matches
    private double avgPointsPerWin;
    private double avgPointsPerLoss;
    
    // Momentum & Consistency metrics
    private double momentumScore;       // Sum of last 7 rating changes
    private double consistencyScore;    // Std dev of rating changes (lower = more consistent)
    private String momentumTrend;       // "hot", "rising", "stable", "cooling", "cold"
    
    // Recent rating changes (last 20)
    private List<RatingChange> recentChanges = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    public OwlRatingDocument() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.rating = 1500.0;
        this.peakRating = 1500.0;
    }

    public OwlRatingDocument(String playerKey, String playerName) {
        this();
        this.playerKey = playerKey;
        this.playerName = playerName;
    }

    public OwlRatingDocument(String playerKey, String playerName, double initialRating) {
        this(playerKey, playerName);
        this.rating = initialRating;
        this.peakRating = initialRating;
    }

    // ============ HELPER METHODS ============

    public void addRatingChange(RatingChange change) {
        this.recentChanges.add(0, change); // Add to front
        if (this.recentChanges.size() > 20) {
            this.recentChanges = new ArrayList<>(this.recentChanges.subList(0, 20));
        }
        this.updatedAt = Instant.now();
    }

    public void updateRating(double pointsChange, boolean won) {
        this.rating += pointsChange;
        this.matchesPlayed++;
        
        if (won) {
            this.wins++;
        } else {
            this.losses++;
        }
        
        // Update peak
        if (this.rating > this.peakRating) {
            this.peakRating = this.rating;
            this.peakDate = LocalDate.now();
        }
        
        // Update rolling stats
        updateRollingStats();
        this.updatedAt = Instant.now();
    }

    private void updateRollingStats() {
        if (recentChanges.isEmpty()) return;
        
        int recentWins = 0;
        double totalWinPoints = 0;
        double totalLossPoints = 0;
        int winCount = 0;
        int lossCount = 0;
        
        int limit = Math.min(10, recentChanges.size());
        for (int i = 0; i < limit; i++) {
            RatingChange rc = recentChanges.get(i);
            if (rc.isWon()) {
                recentWins++;
                totalWinPoints += rc.getPointsChange();
                winCount++;
            } else {
                totalLossPoints += Math.abs(rc.getPointsChange());
                lossCount++;
            }
        }
        
        this.last10WinRate = limit > 0 ? (double) recentWins / limit : 0.5;
        this.avgPointsPerWin = winCount > 0 ? totalWinPoints / winCount : 0;
        this.avgPointsPerLoss = lossCount > 0 ? totalLossPoints / lossCount : 0;
        
        // Calculate momentum (sum of last 7 changes)
        int momentumLimit = Math.min(7, recentChanges.size());
        double momentum = 0;
        for (int i = 0; i < momentumLimit; i++) {
            momentum += recentChanges.get(i).getPointsChange();
        }
        this.momentumScore = momentum;
        
        // Determine momentum trend
        if (momentum >= 30) {
            this.momentumTrend = "hot";
        } else if (momentum >= 10) {
            this.momentumTrend = "rising";
        } else if (momentum >= -10) {
            this.momentumTrend = "stable";
        } else if (momentum >= -30) {
            this.momentumTrend = "cooling";
        } else {
            this.momentumTrend = "cold";
        }
        
        // Calculate consistency (standard deviation of changes)
        if (recentChanges.size() >= 3) {
            double mean = 0;
            for (RatingChange rc : recentChanges) {
                mean += rc.getPointsChange();
            }
            mean /= recentChanges.size();
            
            double sumSquares = 0;
            for (RatingChange rc : recentChanges) {
                sumSquares += Math.pow(rc.getPointsChange() - mean, 2);
            }
            this.consistencyScore = Math.sqrt(sumSquares / recentChanges.size());
        } else {
            this.consistencyScore = 0;
        }
    }

    public String getRatingFormatted() {
        return String.format("%.1f", rating);
    }

    public String getWinLossRecord() {
        return wins + "-" + losses;
    }

    public double getWinRate() {
        return matchesPlayed > 0 ? (double) wins / matchesPlayed : 0.0;
    }

    public String getWinRateFormatted() {
        return String.format("%.1f%%", getWinRate() * 100);
    }

    public String getLast10WinRateFormatted() {
        return String.format("%.0f%%", last10WinRate * 100);
    }

    public String getMomentumEmoji() {
        if (momentumTrend == null) return "➡️";
        return switch (momentumTrend) {
            case "hot" -> "🔥";
            case "rising" -> "📈";
            case "stable" -> "➡️";
            case "cooling" -> "📉";
            case "cold" -> "🧊";
            default -> "➡️";
        };
    }

    public String getMomentumLabel() {
        if (momentumTrend == null) return "Stable";
        return switch (momentumTrend) {
            case "hot" -> "Hot";
            case "rising" -> "Rising";
            case "stable" -> "Stable";
            case "cooling" -> "Cooling";
            case "cold" -> "Cold";
            default -> "Stable";
        };
    }

    public String getMomentumScoreFormatted() {
        return String.format("%+.1f", momentumScore);
    }

    public String getConsistencyLabel() {
        if (consistencyScore < 8) return "Rock Solid";
        if (consistencyScore < 15) return "Steady";
        if (consistencyScore < 25) return "Volatile";
        return "Wild Card";
    }

    public String getConsistencyEmoji() {
        if (consistencyScore < 8) return "🎯";
        if (consistencyScore < 15) return "⚖️";
        if (consistencyScore < 25) return "🎲";
        return "🌪️";
    }

    /**
     * Generate sparkline data - returns list of last N rating values for mini-chart
     */
    public List<Double> getSparklineData() {
        if (recentChanges.isEmpty()) {
            return List.of(rating);
        }
        
        List<Double> sparkline = new ArrayList<>();
        double currentRating = rating;
        
        // Build ratings from current backwards
        int limit = Math.min(10, recentChanges.size());
        for (int i = 0; i < limit; i++) {
            sparkline.add(0, currentRating);
            currentRating -= recentChanges.get(i).getPointsChange();
        }
        sparkline.add(0, currentRating); // Starting point
        
        return sparkline;
    }

    /**
     * Generate SVG polyline points string for sparkline chart.
     */
    public String getSparklinePoints() {
        List<Double> data = getSparklineData();
        if (data == null || data.size() < 2) {
            return "0,10 60,10"; // Flat line if no data
        }

        int width = 60;
        int height = 20;
        
        // Find min/max for scaling
        double min = data.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = data.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        
        double padding = 2;
        double range = max - min;
        if (range == 0) range = 1; // Prevent division by zero
        
        // Calculate points
        StringBuilder points = new StringBuilder();
        int n = data.size();
        
        for (int i = 0; i < n; i++) {
            double x = (i * width) / (double) (n - 1);
            // Invert Y because SVG Y grows downward
            double y = padding + ((max - data.get(i)) / range) * (height - 2 * padding);
            
            if (i > 0) points.append(" ");
            points.append(String.format("%.1f,%.1f", x, y));
        }
        
        return points.toString();
    }

    // ============ GETTERS AND SETTERS ============

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlayerKey() { return playerKey; }
    public void setPlayerKey(String playerKey) { this.playerKey = playerKey; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public Integer getAtpRank() { return atpRank; }
    public void setAtpRank(Integer atpRank) { this.atpRank = atpRank; }

    public int getOwlRank() { return owlRank; }
    public void setOwlRank(int owlRank) { this.owlRank = owlRank; }

    public int getMatchesPlayed() { return matchesPlayed; }
    public void setMatchesPlayed(int matchesPlayed) { this.matchesPlayed = matchesPlayed; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public double getPeakRating() { return peakRating; }
    public void setPeakRating(double peakRating) { this.peakRating = peakRating; }

    public LocalDate getPeakDate() { return peakDate; }
    public void setPeakDate(LocalDate peakDate) { this.peakDate = peakDate; }

    public double getLast10WinRate() { return last10WinRate; }
    public void setLast10WinRate(double last10WinRate) { this.last10WinRate = last10WinRate; }

    public double getAvgPointsPerWin() { return avgPointsPerWin; }
    public void setAvgPointsPerWin(double avgPointsPerWin) { this.avgPointsPerWin = avgPointsPerWin; }

    public double getAvgPointsPerLoss() { return avgPointsPerLoss; }
    public void setAvgPointsPerLoss(double avgPointsPerLoss) { this.avgPointsPerLoss = avgPointsPerLoss; }

    public List<RatingChange> getRecentChanges() { return recentChanges; }
    public void setRecentChanges(List<RatingChange> recentChanges) { this.recentChanges = recentChanges; }

    public double getMomentumScore() { return momentumScore; }
    public void setMomentumScore(double momentumScore) { this.momentumScore = momentumScore; }

    public double getConsistencyScore() { return consistencyScore; }
    public void setConsistencyScore(double consistencyScore) { this.consistencyScore = consistencyScore; }

    public String getMomentumTrend() { return momentumTrend; }
    public void setMomentumTrend(String momentumTrend) { this.momentumTrend = momentumTrend; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

