package com.tennis.prediction.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Stores prediction results (after match completion).
 * Links predictions to actual outcomes for model evaluation.
 */
@Document(collection = "prediction_results")
@CompoundIndex(name = "match_model_result_idx", def = "{'matchKey': 1, 'modelId': 1}", unique = true)
public class PredictionResultDocument {

    @Id
    private String id;

    @Indexed
    private String matchKey;

    @Indexed
    private String modelId;

    private String predictionId;  // Reference to PredictionDocument
    
    private LocalDate matchDate;
    private String predictedWinner;
    private String actualWinner;
    private boolean correct;

    // Prediction details at time of prediction
    private double predictedProbability;  // Probability assigned to predicted winner
    private double confidence;

    // For Brier score calculation
    private double brierScore;  // (predicted_prob - actual)^2

    // Betting simulation fields
    private Double homeOdds;           // Player 1 odds at time of match
    private Double awayOdds;           // Player 2 odds at time of match
    private Double predictedOdds;      // Odds for the predicted winner
    private double stake = 1.0;        // Bet amount (always 1 token)
    private Double profit;             // +odds-1 if correct, -stake if wrong
    private boolean betPlaced = false; // Whether a bet was placed (considering filters)
    
    // Player names for display
    private String player1Name;
    private String player2Name;
    private String predictedWinnerName;
    private String actualWinnerName;

    private Instant evaluatedAt;

    public PredictionResultDocument() {
        this.evaluatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMatchKey() { return matchKey; }
    public void setMatchKey(String matchKey) { this.matchKey = matchKey; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getPredictionId() { return predictionId; }
    public void setPredictionId(String predictionId) { this.predictionId = predictionId; }

    public LocalDate getMatchDate() { return matchDate; }
    public void setMatchDate(LocalDate matchDate) { this.matchDate = matchDate; }

    public String getPredictedWinner() { return predictedWinner; }
    public void setPredictedWinner(String predictedWinner) { this.predictedWinner = predictedWinner; }

    public String getActualWinner() { return actualWinner; }
    public void setActualWinner(String actualWinner) { this.actualWinner = actualWinner; }

    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }

    public double getPredictedProbability() { return predictedProbability; }
    public void setPredictedProbability(double predictedProbability) { this.predictedProbability = predictedProbability; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public double getBrierScore() { return brierScore; }
    public void setBrierScore(double brierScore) { this.brierScore = brierScore; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    // Betting simulation getters/setters
    public Double getHomeOdds() { return homeOdds; }
    public void setHomeOdds(Double homeOdds) { this.homeOdds = homeOdds; }

    public Double getAwayOdds() { return awayOdds; }
    public void setAwayOdds(Double awayOdds) { this.awayOdds = awayOdds; }

    public Double getPredictedOdds() { return predictedOdds; }
    public void setPredictedOdds(Double predictedOdds) { this.predictedOdds = predictedOdds; }

    public double getStake() { return stake; }
    public void setStake(double stake) { this.stake = stake; }

    public Double getProfit() { return profit; }
    public void setProfit(Double profit) { this.profit = profit; }

    public boolean isBetPlaced() { return betPlaced; }
    public void setBetPlaced(boolean betPlaced) { this.betPlaced = betPlaced; }

    public String getPlayer1Name() { return player1Name; }
    public void setPlayer1Name(String player1Name) { this.player1Name = player1Name; }

    public String getPlayer2Name() { return player2Name; }
    public void setPlayer2Name(String player2Name) { this.player2Name = player2Name; }

    public String getPredictedWinnerName() { return predictedWinnerName; }
    public void setPredictedWinnerName(String predictedWinnerName) { this.predictedWinnerName = predictedWinnerName; }

    public String getActualWinnerName() { return actualWinnerName; }
    public void setActualWinnerName(String actualWinnerName) { this.actualWinnerName = actualWinnerName; }

    // Helper for formatted profit display
    public String getProfitFormatted() {
        if (profit == null) return "N/A";
        return String.format("%+.2f", profit);
    }

    public boolean hasOdds() {
        return homeOdds != null && awayOdds != null;
    }
}

