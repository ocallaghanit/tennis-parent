package com.tennis.prediction.service;

import com.tennis.prediction.model.PredictionDocument;
import com.tennis.prediction.model.PredictionResultDocument;
import com.tennis.prediction.model.readonly.FixtureDocument;
import com.tennis.prediction.model.readonly.OddsDocument;
import com.tennis.prediction.repository.PredictionResultRepository;
import com.tennis.prediction.repository.readonly.FixtureReadRepository;
import com.tennis.prediction.repository.readonly.OddsReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for backtesting prediction models on historical data.
 */
@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private final FixtureReadRepository fixtureRepository;
    private final PredictionService predictionService;
    private final PredictionResultRepository resultRepository;
    private final OddsReadRepository oddsRepository;

    public BacktestService(
            FixtureReadRepository fixtureRepository,
            PredictionService predictionService,
            PredictionResultRepository resultRepository,
            OddsReadRepository oddsRepository
    ) {
        this.fixtureRepository = fixtureRepository;
        this.predictionService = predictionService;
        this.resultRepository = resultRepository;
        this.oddsRepository = oddsRepository;
    }

    /**
     * Run a backtest for a model on historical data with default minOdds (no filter).
     */
    public BacktestResult runBacktest(String modelId, LocalDate startDate, LocalDate endDate) {
        return runBacktest(modelId, startDate, endDate, null);
    }

    /**
     * Run a backtest for a model on historical data with optional minimum odds filter.
     * @param minOdds If provided, only place bets when predicted winner's odds >= minOdds
     */
    public BacktestResult runBacktest(String modelId, LocalDate startDate, LocalDate endDate, Double minOdds) {
        log.info("Running backtest for model {} from {} to {} (minOdds={})", modelId, startDate, endDate, minOdds);

        // Get finished matches in date range
        List<FixtureDocument> finishedMatches = fixtureRepository.findFinishedByDateRange(startDate, endDate);
        
        log.info("Found {} finished matches for backtesting", finishedMatches.size());

        // Pre-fetch all odds for efficiency
        List<String> matchKeys = finishedMatches.stream()
                .map(FixtureDocument::getEventKey)
                .collect(Collectors.toList());
        Map<String, OddsDocument> oddsMap = oddsRepository.findByMatchKeyIn(matchKeys).stream()
                .collect(Collectors.toMap(OddsDocument::getMatchKey, o -> o, (a, b) -> a));
        log.info("Found odds for {} matches", oddsMap.size());

        List<PredictionResultDocument> results = new ArrayList<>();
        int correct = 0;
        int incorrect = 0;
        int skipped = 0;
        double totalBrierScore = 0.0;
        
        // Betting metrics
        int matchesWithOdds = 0;
        int betsPlaced = 0;
        int skippedDueToOddsFilter = 0;
        double totalStake = 0.0;
        double totalProfit = 0.0;

        for (FixtureDocument fixture : finishedMatches) {
            try {
                // Skip if no winner recorded
                if (fixture.getWinner() == null) {
                    skipped++;
                    continue;
                }

                // Generate prediction as if we were predicting before the match
                PredictionDocument prediction = predictionService.generatePredictionForBacktest(
                        fixture, modelId, fixture.getEventDate());

                // Determine actual winner
                String actualWinner = determineActualWinner(fixture);
                if (actualWinner == null) {
                    skipped++;
                    continue;
                }

                // Check if prediction was correct
                boolean isCorrect = prediction.getPredictedWinner().equals(actualWinner);

                // Calculate Brier score
                double predictedProb = actualWinner.equals(fixture.getFirstPlayerKey())
                        ? prediction.getPlayer1WinProbability()
                        : prediction.getPlayer2WinProbability();
                double brierScore = Math.pow(1.0 - predictedProb, 2);

                // Create result document
                PredictionResultDocument result = new PredictionResultDocument();
                result.setMatchKey(fixture.getEventKey());
                result.setModelId(modelId);
                result.setMatchDate(fixture.getEventDate());
                result.setPredictedWinner(prediction.getPredictedWinner());
                result.setActualWinner(actualWinner);
                result.setCorrect(isCorrect);
                result.setPredictedProbability(predictedProb);
                result.setConfidence(prediction.getConfidence());
                result.setBrierScore(brierScore);
                result.setEvaluatedAt(Instant.now());
                
                // Set player names for display
                result.setPlayer1Name(fixture.getFirstPlayerName());
                result.setPlayer2Name(fixture.getSecondPlayerName());
                result.setPredictedWinnerName(
                        prediction.getPredictedWinner().equals(fixture.getFirstPlayerKey())
                                ? fixture.getFirstPlayerName() : fixture.getSecondPlayerName());
                result.setActualWinnerName(
                        actualWinner.equals(fixture.getFirstPlayerKey())
                                ? fixture.getFirstPlayerName() : fixture.getSecondPlayerName());

                // Fetch and apply odds for betting simulation
                OddsDocument odds = oddsMap.get(fixture.getEventKey());
                if (odds != null && odds.getHomeOdds() != null && odds.getAwayOdds() != null) {
                    result.setHomeOdds(odds.getHomeOdds());
                    result.setAwayOdds(odds.getAwayOdds());
                    
                    // Determine odds for predicted winner (home = player1, away = player2)
                    Double predictedOdds = prediction.getPredictedWinner().equals(fixture.getFirstPlayerKey())
                            ? odds.getHomeOdds() : odds.getAwayOdds();
                    result.setPredictedOdds(predictedOdds);
                    
                    matchesWithOdds++;
                    
                    // Apply minimum odds filter
                    if (minOdds != null && predictedOdds < minOdds) {
                        // Odds below threshold - don't place bet
                        result.setBetPlaced(false);
                        skippedDueToOddsFilter++;
                    } else {
                        // Place the bet
                        result.setBetPlaced(true);
                        
                        // Calculate profit: WIN = odds - stake, LOSE = -stake
                        double stake = result.getStake();
                        double profit = isCorrect ? (predictedOdds - stake) : -stake;
                        result.setProfit(profit);
                        
                        // Aggregate betting metrics
                        betsPlaced++;
                        totalStake += stake;
                        totalProfit += profit;
                    }
                }

                results.add(result);

                if (isCorrect) {
                    correct++;
                } else {
                    incorrect++;
                }
                totalBrierScore += brierScore;

            } catch (Exception e) {
                log.warn("Error processing match {}: {}", fixture.getEventKey(), e.getMessage());
                skipped++;
            }
        }

        // Save results
        resultRepository.saveAll(results);

        // Calculate metrics
        int total = correct + incorrect;
        double accuracy = total > 0 ? (double) correct / total : 0.0;
        double avgBrierScore = total > 0 ? totalBrierScore / total : 0.0;
        double roi = totalStake > 0 ? (totalProfit / totalStake) * 100 : 0.0;

        return new BacktestResult(
                modelId,
                startDate,
                endDate,
                total,
                correct,
                incorrect,
                skipped,
                accuracy,
                avgBrierScore,
                matchesWithOdds,
                betsPlaced,
                skippedDueToOddsFilter,
                minOdds,
                totalStake,
                totalProfit,
                roi,
                results
        );
    }

    /**
     * Get backtest results for a model.
     */
    public List<PredictionResultDocument> getResults(String modelId, LocalDate startDate, LocalDate endDate) {
        return resultRepository.findByDateRangeAndModel(startDate, endDate, modelId);
    }

    /**
     * Calculate model accuracy from stored results.
     */
    public double getModelAccuracy(String modelId) {
        return resultRepository.calculateAccuracy(modelId);
    }

    /**
     * Compare multiple models on the same date range.
     */
    public Map<String, BacktestResult> compareModels(List<String> modelIds, LocalDate startDate, LocalDate endDate) {
        Map<String, BacktestResult> results = new LinkedHashMap<>();
        
        for (String modelId : modelIds) {
            results.put(modelId, runBacktest(modelId, startDate, endDate));
        }
        
        return results;
    }

    /**
     * Analyze predictions by confidence level.
     */
    public Map<String, ConfidenceBucket> analyzeByConfidence(String modelId) {
        List<PredictionResultDocument> allResults = resultRepository.findByModelId(modelId);
        
        // Group by confidence buckets
        Map<String, List<PredictionResultDocument>> buckets = new LinkedHashMap<>();
        buckets.put("0.3-0.4", new ArrayList<>());
        buckets.put("0.4-0.5", new ArrayList<>());
        buckets.put("0.5-0.6", new ArrayList<>());
        buckets.put("0.6-0.7", new ArrayList<>());
        buckets.put("0.7-0.8", new ArrayList<>());
        buckets.put("0.8-0.9", new ArrayList<>());
        buckets.put("0.9-1.0", new ArrayList<>());

        for (PredictionResultDocument result : allResults) {
            double conf = result.getConfidence();
            if (conf < 0.4) buckets.get("0.3-0.4").add(result);
            else if (conf < 0.5) buckets.get("0.4-0.5").add(result);
            else if (conf < 0.6) buckets.get("0.5-0.6").add(result);
            else if (conf < 0.7) buckets.get("0.6-0.7").add(result);
            else if (conf < 0.8) buckets.get("0.7-0.8").add(result);
            else if (conf < 0.9) buckets.get("0.8-0.9").add(result);
            else buckets.get("0.9-1.0").add(result);
        }

        // Calculate accuracy for each bucket
        Map<String, ConfidenceBucket> analysis = new LinkedHashMap<>();
        for (Map.Entry<String, List<PredictionResultDocument>> entry : buckets.entrySet()) {
            List<PredictionResultDocument> bucket = entry.getValue();
            int total = bucket.size();
            int correct = (int) bucket.stream().filter(PredictionResultDocument::isCorrect).count();
            double accuracy = total > 0 ? (double) correct / total : 0.0;
            analysis.put(entry.getKey(), new ConfidenceBucket(entry.getKey(), total, correct, accuracy));
        }

        return analysis;
    }

    private String determineActualWinner(FixtureDocument fixture) {
        String winner = fixture.getWinner();
        if (winner == null) return null;

        // Handle different winner formats
        if (winner.equals(fixture.getFirstPlayerKey()) || winner.equals(fixture.getSecondPlayerKey())) {
            return winner;
        }
        if ("First Player".equals(winner)) {
            return fixture.getFirstPlayerKey();
        }
        if ("Second Player".equals(winner)) {
            return fixture.getSecondPlayerKey();
        }
        return null;
    }

    // ============ RESULT CLASSES ============

    public record BacktestResult(
            String modelId,
            LocalDate startDate,
            LocalDate endDate,
            int totalMatches,
            int correct,
            int incorrect,
            int skipped,
            double accuracy,
            double avgBrierScore,
            // Betting simulation metrics
            int matchesWithOdds,
            int betsPlaced,
            int skippedDueToOddsFilter,
            Double minOddsFilter,  // The minimum odds filter applied (null if no filter)
            double totalStake,
            double totalProfit,
            double roi,  // Return on Investment %
            List<PredictionResultDocument> predictions
    ) {
        public String getAccuracyPercent() {
            return String.format("%.1f%%", accuracy * 100);
        }
        
        public String getRoiPercent() {
            return String.format("%+.2f%%", roi);
        }
        
        public String getProfitFormatted() {
            return String.format("%+.2f", totalProfit);
        }
        
        public String getStakeFormatted() {
            return String.format("%.0f", totalStake);
        }
        
        public String getMinOddsFormatted() {
            return minOddsFilter != null ? String.format("%.2f", minOddsFilter) : "None";
        }
        
        public boolean hasOddsFilter() {
            return minOddsFilter != null && minOddsFilter > 1.0;
        }
    }

    public record ConfidenceBucket(
            String range,
            int total,
            int correct,
            double accuracy
    ) {
        public String getAccuracyPercent() {
            return String.format("%.1f%%", accuracy * 100);
        }
    }
}

