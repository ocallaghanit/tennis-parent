package com.tennis.prediction.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Stores match predictions.
 * This service has full read/write access to this collection.
 */
@Document(collection = "predictions")
@CompoundIndex(name = "match_model_idx", def = "{'matchKey': 1, 'modelId': 1}", unique = true)
public class PredictionDocument {

    @Id
    private String id;

    @Indexed
    private String matchKey;

    @Indexed
    private String modelId;

    private LocalDate matchDate;
    private String player1Key;
    private String player1Name;
    private String player2Key;
    private String player2Name;

    // Prediction output
    private String predictedWinner;      // player key
    private double player1WinProbability;
    private double player2WinProbability;
    private double confidence;           // 0-1 confidence score

    // Factor contributions (for explainability)
    private Map<String, Double> factorScores;

    // Metadata
    private Instant createdAt;
    private Instant updatedAt;

    public PredictionDocument() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMatchKey() { return matchKey; }
    public void setMatchKey(String matchKey) { this.matchKey = matchKey; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public LocalDate getMatchDate() { return matchDate; }
    public void setMatchDate(LocalDate matchDate) { this.matchDate = matchDate; }

    public String getPlayer1Key() { return player1Key; }
    public void setPlayer1Key(String player1Key) { this.player1Key = player1Key; }

    public String getPlayer1Name() { return player1Name; }
    public void setPlayer1Name(String player1Name) { this.player1Name = player1Name; }

    public String getPlayer2Key() { return player2Key; }
    public void setPlayer2Key(String player2Key) { this.player2Key = player2Key; }

    public String getPlayer2Name() { return player2Name; }
    public void setPlayer2Name(String player2Name) { this.player2Name = player2Name; }

    public String getPredictedWinner() { return predictedWinner; }
    public void setPredictedWinner(String predictedWinner) { this.predictedWinner = predictedWinner; }

    public double getPlayer1WinProbability() { return player1WinProbability; }
    public void setPlayer1WinProbability(double player1WinProbability) { this.player1WinProbability = player1WinProbability; }

    public double getPlayer2WinProbability() { return player2WinProbability; }
    public void setPlayer2WinProbability(double player2WinProbability) { this.player2WinProbability = player2WinProbability; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public Map<String, Double> getFactorScores() { return factorScores; }
    public void setFactorScores(Map<String, Double> factorScores) { this.factorScores = factorScores; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

