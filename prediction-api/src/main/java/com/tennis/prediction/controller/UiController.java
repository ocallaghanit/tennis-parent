package com.tennis.prediction.controller;

import com.tennis.prediction.model.ModelConfigDocument;
import com.tennis.prediction.model.OwlRatingDocument;
import com.tennis.prediction.model.PredictionDocument;
import com.tennis.prediction.model.readonly.FixtureDocument;
import com.tennis.prediction.model.readonly.OddsDocument;
import com.tennis.prediction.repository.OwlRatingRepository;
import com.tennis.prediction.repository.PredictionRepository;
import com.tennis.prediction.repository.PredictionResultRepository;
import com.tennis.prediction.repository.readonly.FixtureReadRepository;
import com.tennis.prediction.repository.readonly.OddsReadRepository;
import com.tennis.prediction.service.BacktestService;
import com.tennis.prediction.service.ModelService;
import com.tennis.prediction.service.OwlRatingService;
import com.tennis.prediction.service.PredictionService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ui")
public class UiController {

    private static final Logger log = LoggerFactory.getLogger(UiController.class);

    private final PredictionService predictionService;
    private final ModelService modelService;
    private final BacktestService backtestService;
    private final OwlRatingService owlRatingService;
    private final PredictionRepository predictionRepository;
    private final PredictionResultRepository resultRepository;
    private final FixtureReadRepository fixtureRepository;
    private final OwlRatingRepository owlRatingRepository;
    private final OddsReadRepository oddsRepository;
    private final RestTemplate restTemplate;
    
    @Value("${adapter.api.url:http://api-tennis-simple-adapter:8080}")
    private String adapterApiUrl;

    public UiController(
            PredictionService predictionService,
            ModelService modelService,
            BacktestService backtestService,
            OwlRatingService owlRatingService,
            PredictionRepository predictionRepository,
            PredictionResultRepository resultRepository,
            FixtureReadRepository fixtureRepository,
            OwlRatingRepository owlRatingRepository,
            OddsReadRepository oddsRepository,
            RestTemplate restTemplate
    ) {
        this.predictionService = predictionService;
        this.modelService = modelService;
        this.backtestService = backtestService;
        this.owlRatingService = owlRatingService;
        this.predictionRepository = predictionRepository;
        this.resultRepository = resultRepository;
        this.fixtureRepository = fixtureRepository;
        this.owlRatingRepository = owlRatingRepository;
        this.oddsRepository = oddsRepository;
        this.restTemplate = restTemplate;
    }

    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        // Stats
        model.addAttribute("predictionCount", predictionRepository.count());
        model.addAttribute("resultCount", resultRepository.count());
        model.addAttribute("fixtureCount", fixtureRepository.count());

        // Models
        List<ModelConfigDocument> models = modelService.getAllModels();
        model.addAttribute("models", models);
        model.addAttribute("activeModel", modelService.getActiveModel());

        // Upcoming matches
        List<FixtureDocument> upcoming = predictionService.getUpcomingMatches(7);
        model.addAttribute("upcomingMatches", upcoming);
        model.addAttribute("upcomingCount", upcoming.size());

        // Recent predictions
        List<PredictionDocument> recentPredictions = predictionRepository.findAll().stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(10)
                .collect(Collectors.toList());
        model.addAttribute("recentPredictions", recentPredictions);

        return "dashboard";
    }

    @GetMapping("/predict")
    public String predict(
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            Model model
    ) {
        // Default date range: today to 7 days ahead
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now();
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now().plusDays(7);
        
        // Get all models
        List<ModelConfigDocument> models = modelService.getAllModels();
        model.addAttribute("models", models);
        model.addAttribute("selectedModel", modelId != null ? modelId : "balanced");

        // Get unfinished matches in date range
        List<FixtureDocument> upcoming = predictionService.getUnfinishedMatches(startDate, endDate);
        model.addAttribute("upcomingMatches", upcoming);
        
        // Pass date range back to template
        model.addAttribute("dateStart", startDate);
        model.addAttribute("dateEnd", endDate);

        return "predict";
    }

    @PostMapping("/predict")
    public String doPrediction(
            @RequestParam String matchKey,
            @RequestParam(defaultValue = "balanced") String modelId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            Model model
    ) {
        try {
            PredictionDocument prediction = predictionService.predict(matchKey, modelId);
            model.addAttribute("prediction", prediction);
            model.addAttribute("success", true);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        // Default date range
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now();
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now().plusDays(7);

        // Reload page data
        List<ModelConfigDocument> models = modelService.getAllModels();
        model.addAttribute("models", models);
        model.addAttribute("selectedModel", modelId);
        model.addAttribute("upcomingMatches", predictionService.getUnfinishedMatches(startDate, endDate));
        model.addAttribute("dateStart", startDate);
        model.addAttribute("dateEnd", endDate);

        return "predict";
    }

    @PostMapping("/predict-batch")
    public String predictBatch(
            @RequestParam(defaultValue = "balanced") String modelId,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            Model model
    ) {
        List<PredictionDocument> predictions = predictionService.predictUpcoming(modelId, days);
        model.addAttribute("batchPredictions", predictions);
        model.addAttribute("batchCount", predictions.size());

        // Default date range
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now();
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now().plusDays(7);

        // Reload page data
        model.addAttribute("models", modelService.getAllModels());
        model.addAttribute("selectedModel", modelId);
        model.addAttribute("upcomingMatches", predictionService.getUnfinishedMatches(startDate, endDate));
        model.addAttribute("dateStart", startDate);
        model.addAttribute("dateEnd", endDate);

        return "predict";
    }

    /**
     * GET handler for predict-all-models - redirects to predict page with date params.
     */
    @GetMapping("/predict-all-models")
    public String predictAllModelsGet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd
    ) {
        String redirect = "redirect:/ui/predict";
        if (dateStart != null || dateEnd != null) {
            redirect += "?";
            if (dateStart != null) redirect += "dateStart=" + dateStart;
            if (dateStart != null && dateEnd != null) redirect += "&";
            if (dateEnd != null) redirect += "dateEnd=" + dateEnd;
        }
        return redirect;
    }

    /**
     * Run predictions for all upcoming matches against ALL models with odds.
     * Displays results in a table with filtering options.
     */
    @PostMapping("/predict-all-models")
    public String predictAllModels(
            @RequestParam(required = false) Double minOdds,
            @RequestParam(required = false) Double maxOdds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            Model model
    ) {
        // Default date range
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now();
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now().plusDays(7);

        // Generate predictions using the date range
        List<MultiModelPrediction> allPredictions = generateMultiModelPredictions(startDate, endDate, minOdds, maxOdds);
        
        model.addAttribute("multiModelPredictions", allPredictions);
        model.addAttribute("multiModelCount", allPredictions.size());
        model.addAttribute("minOddsFilter", minOdds);
        model.addAttribute("maxOddsFilter", maxOdds);

        // Reload page data
        model.addAttribute("models", modelService.getAllModels());
        model.addAttribute("upcomingMatches", predictionService.getUnfinishedMatches(startDate, endDate));
        model.addAttribute("dateStart", startDate);
        model.addAttribute("dateEnd", endDate);

        return "predict";
    }

    /**
     * Export multi-model predictions as CSV.
     * Includes all keys needed for verification/import into api-tennis-simple-adapter.
     * @param modelIds Optional comma-separated list of model IDs to include
     */
    @GetMapping("/predict-all-models/export")
    public void exportPredictionsCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            @RequestParam(required = false) Double minOdds,
            @RequestParam(required = false) Double maxOdds,
            @RequestParam(required = false) String modelIds,
            HttpServletResponse response
    ) throws IOException {
        // Use date range, defaulting to today + 7 days if not provided
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now();
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now().plusDays(7);
        
        // Parse model IDs if provided
        List<String> selectedModelIds = null;
        if (modelIds != null && !modelIds.isBlank()) {
            selectedModelIds = Arrays.stream(modelIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        List<MultiModelPrediction> predictions = generateMultiModelPredictions(startDate, endDate, minOdds, maxOdds, selectedModelIds);
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"predictions_" + LocalDate.now() + ".csv\"");
        
        PrintWriter writer = response.getWriter();
        
        // CSV Header - includes keys for verification/import
        writer.println("Match Key,Match Date,Tournament Key,Player 1 Key,Player 1 Name,Player 2 Key,Player 2 Name," +
                      "Model ID,Model Name,Predicted Winner Key,Predicted Winner Name,Win Probability,Confidence," +
                      "Player 1 Odds,Player 2 Odds,Predicted Winner Odds,Potential Profit");
        
        for (MultiModelPrediction pred : predictions) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.4f,%.4f,%s,%s,%s,%s%n",
                escapeCsv(pred.matchKey),
                escapeCsv(pred.matchDate != null ? pred.matchDate.toString() : ""),
                escapeCsv(pred.tournamentKey != null ? pred.tournamentKey : ""),
                escapeCsv(pred.player1Key),
                escapeCsv(pred.player1Name),
                escapeCsv(pred.player2Key),
                escapeCsv(pred.player2Name),
                escapeCsv(pred.modelId),
                escapeCsv(pred.modelName),
                escapeCsv(pred.predictedWinnerKey),
                escapeCsv(pred.predictedWinnerName),
                pred.winProbability,
                pred.confidence,
                pred.player1Odds != null ? String.format("%.2f", pred.player1Odds) : "",
                pred.player2Odds != null ? String.format("%.2f", pred.player2Odds) : "",
                pred.predictedWinnerOdds != null ? String.format("%.2f", pred.predictedWinnerOdds) : "",
                pred.predictedWinnerOdds != null ? String.format("%.2f", pred.predictedWinnerOdds - 1.0) : ""
            );
        }
        
        writer.flush();
    }

    /**
     * Verify predictions by fetching actual results from the adapter API.
     * This calls the adapter to sync fixtures, then compares predictions with actual results.
     */
    @PostMapping("/predict-verify")
    public String verifyPredictions(
            @RequestParam(required = false) Double minOdds,
            @RequestParam(required = false) Double maxOdds,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateEnd,
            Model model
    ) {
        LocalDate startDate = dateStart != null ? dateStart : LocalDate.now().minusDays(7);
        LocalDate endDate = dateEnd != null ? dateEnd : LocalDate.now();
        
        log.info("Verifying predictions from {} to {}", startDate, endDate);
        
        // Step 1: Call adapter API to sync fixtures for the date range
        String syncMessage = null;
        try {
            String syncUrl = adapterApiUrl + "/api/ingest/fixtures?dateStart=" + startDate + "&dateStop=" + endDate + "&batched=true";
            log.info("Calling adapter ingest API: {}", syncUrl);
            restTemplate.postForObject(syncUrl, null, String.class);
            syncMessage = "✓ Synced fixtures from API";
        } catch (Exception e) {
            log.warn("Failed to sync from adapter API: {}", e.getMessage());
            syncMessage = "⚠ Sync failed: " + e.getMessage();
        }
        
        // Step 2: Get ALL matches in date range (not just unfinished)
        List<FixtureDocument> allMatches = fixtureRepository.findByDateRange(startDate, endDate);
        Map<String, FixtureDocument> matchMap = allMatches.stream()
                .collect(Collectors.toMap(FixtureDocument::getEventKey, f -> f, (a, b) -> a));
        
        // Step 3: Generate predictions and verify against actual results
        List<MultiModelPrediction> verifiedPredictions = new ArrayList<>();
        VerificationStats stats = new VerificationStats();
        
        List<ModelConfigDocument> models = modelService.getAllModels();
        List<String> matchKeys = allMatches.stream()
                .map(FixtureDocument::getEventKey)
                .collect(Collectors.toList());
        Map<String, OddsDocument> oddsMap = oddsRepository.findByMatchKeyIn(matchKeys).stream()
                .collect(Collectors.toMap(OddsDocument::getMatchKey, o -> o, (a, b) -> a));
        
        for (FixtureDocument fixture : allMatches) {
            OddsDocument odds = oddsMap.get(fixture.getEventKey());
            
            for (ModelConfigDocument modelConfig : models) {
                try {
                    PredictionDocument pred = predictionService.predict(fixture.getEventKey(), modelConfig.getModelId());
                    
                    MultiModelPrediction mmp = new MultiModelPrediction();
                    mmp.matchKey = fixture.getEventKey();
                    mmp.matchDate = fixture.getEventDate();
                    mmp.tournamentKey = fixture.getTournamentKey();
                    mmp.player1Key = pred.getPlayer1Key();
                    mmp.player1Name = pred.getPlayer1Name();
                    mmp.player2Key = pred.getPlayer2Key();
                    mmp.player2Name = pred.getPlayer2Name();
                    mmp.modelId = modelConfig.getModelId();
                    mmp.modelName = modelConfig.getName();
                    mmp.predictedWinnerKey = pred.getPredictedWinner();
                    mmp.predictedWinnerName = pred.getPredictedWinner().equals(pred.getPlayer1Key()) 
                        ? pred.getPlayer1Name() : pred.getPlayer2Name();
                    mmp.winProbability = pred.getPredictedWinner().equals(pred.getPlayer1Key())
                        ? pred.getPlayer1WinProbability() : pred.getPlayer2WinProbability();
                    mmp.confidence = pred.getConfidence();
                    
                    // Add odds
                    if (odds != null) {
                        mmp.player1Odds = odds.getHomeOdds();
                        mmp.player2Odds = odds.getAwayOdds();
                        mmp.predictedWinnerOdds = pred.getPredictedWinner().equals(pred.getPlayer1Key())
                            ? odds.getHomeOdds() : odds.getAwayOdds();
                    }
                    
                    // Skip if odds filter doesn't match (range filter)
                    if (mmp.predictedWinnerOdds == null) {
                        if (minOdds != null || maxOdds != null) continue;
                    } else {
                        if (minOdds != null && mmp.predictedWinnerOdds < minOdds) continue;
                        if (maxOdds != null && mmp.predictedWinnerOdds > maxOdds) continue;
                    }
                    
                    // Verification: check actual result
                    mmp.status = fixture.getStatus();
                    ModelStats modelStats = stats.getOrCreateModelStats(modelConfig.getModelId(), modelConfig.getName());
                    
                    if ("Finished".equalsIgnoreCase(fixture.getStatus()) || 
                        "Retired".equalsIgnoreCase(fixture.getStatus())) {
                        
                        String actualWinner = fixture.getWinner();
                        if (actualWinner != null) {
                            mmp.actualWinnerKey = actualWinner;
                            mmp.actualWinnerName = actualWinner.equals(fixture.getFirstPlayerKey()) 
                                ? fixture.getFirstPlayerName() : fixture.getSecondPlayerName();
                            mmp.actualScore = fixture.getScore();
                            
                            mmp.isCorrect = mmp.predictedWinnerKey.equals(actualWinner);
                            
                            // Calculate profit/loss
                            if (mmp.predictedWinnerOdds != null) {
                                if (mmp.isCorrect) {
                                    mmp.profit = mmp.predictedWinnerOdds - 1.0;
                                } else {
                                    mmp.profit = -1.0;
                                }
                                stats.totalStaked += 1.0;
                                stats.totalReturn += mmp.isCorrect ? mmp.predictedWinnerOdds : 0.0;
                                
                                // Track per-model stats
                                modelStats.totalStaked += 1.0;
                                modelStats.totalReturn += mmp.isCorrect ? mmp.predictedWinnerOdds : 0.0;
                                
                                // Track per-odds-range stats (global and per-model)
                                stats.addOddsRangeBet(mmp.predictedWinnerOdds, mmp.isCorrect);
                                modelStats.addOddsRangeBet(mmp.predictedWinnerOdds, mmp.isCorrect);
                            }
                            
                            stats.totalVerified++;
                            modelStats.totalVerified++;
                            if (mmp.isCorrect) {
                                stats.correct++;
                                modelStats.correct++;
                            } else {
                                stats.incorrect++;
                                modelStats.incorrect++;
                            }
                        }
                    } else {
                        stats.notFinished++;
                    }
                    
                    stats.totalPredictions++;
                    modelStats.totalPredictions++;
                    verifiedPredictions.add(mmp);
                    
                } catch (Exception e) {
                    log.debug("Failed to predict match {}: {}", fixture.getEventKey(), e.getMessage());
                }
            }
        }
        
        // Calculate final stats
        stats.calculate();
        
        // Add all data to model
        model.addAttribute("verifiedPredictions", verifiedPredictions);
        model.addAttribute("verificationStats", stats);
        model.addAttribute("syncMessage", syncMessage);
        model.addAttribute("minOddsFilter", minOdds);
        model.addAttribute("maxOddsFilter", maxOdds);
        model.addAttribute("dateStart", startDate);
        model.addAttribute("dateEnd", endDate);
        model.addAttribute("verified", true);
        
        // Reload standard page data
        model.addAttribute("models", modelService.getAllModels());
        model.addAttribute("upcomingMatches", predictionService.getUnfinishedMatches(startDate, endDate));
        
        return "predict";
    }

    /**
     * Verification statistics class.
     */
    public static class VerificationStats {
        public int totalPredictions;
        public int totalVerified;
        public int correct;
        public int incorrect;
        public int notFinished;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        // Per-model stats
        public Map<String, ModelStats> modelStats = new LinkedHashMap<>();
        
        // Per-odds-range stats
        public Map<String, OddsRangeStats> oddsRangeStats = new LinkedHashMap<>();
        
        public VerificationStats() {
            // Initialize standard odds range buckets
            oddsRangeStats.put("1.0-1.5", new OddsRangeStats("1.0-1.5", 1.0, 1.5));
            oddsRangeStats.put("1.5-2.0", new OddsRangeStats("1.5-2.0", 1.5, 2.0));
            oddsRangeStats.put("2.0-2.5", new OddsRangeStats("2.0-2.5", 2.0, 2.5));
            oddsRangeStats.put("2.5-3.0", new OddsRangeStats("2.5-3.0", 2.5, 3.0));
            oddsRangeStats.put("3.0-4.0", new OddsRangeStats("3.0-4.0", 3.0, 4.0));
            oddsRangeStats.put("4.0+", new OddsRangeStats("4.0+", 4.0, 100.0));
        }
        
        public void calculate() {
            accuracy = totalVerified > 0 ? (double) correct / totalVerified : 0;
            profit = totalReturn - totalStaked;
            roi = totalStaked > 0 ? (profit / totalStaked) * 100 : 0;
            
            // Calculate per-model stats
            for (ModelStats ms : modelStats.values()) {
                ms.calculate();
            }
            
            // Calculate per-odds-range stats
            for (OddsRangeStats ors : oddsRangeStats.values()) {
                ors.calculate();
            }
        }
        
        public ModelStats getOrCreateModelStats(String modelId, String modelName) {
            return modelStats.computeIfAbsent(modelId, k -> {
                ModelStats ms = new ModelStats();
                ms.modelId = modelId;
                ms.modelName = modelName;
                return ms;
            });
        }
        
        public void addOddsRangeBet(double odds, boolean isCorrect) {
            for (OddsRangeStats ors : oddsRangeStats.values()) {
                if (odds >= ors.minOdds && odds < ors.maxOdds) {
                    ors.addBet(isCorrect, odds);
                    break;
                }
            }
        }
        
        // Getters for Thymeleaf
        public int getTotalPredictions() { return totalPredictions; }
        public int getTotalVerified() { return totalVerified; }
        public int getCorrect() { return correct; }
        public int getIncorrect() { return incorrect; }
        public int getNotFinished() { return notFinished; }
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getTotalStakedFormatted() { return String.format("%.0f", totalStaked); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
        public List<ModelStats> getModelStatsList() { 
            // Sort by profit descending (best models first)
            return modelStats.values().stream()
                .sorted((a, b) -> Double.compare(b.profit, a.profit))
                .collect(Collectors.toList());
        }
        
        public List<OddsRangeStats> getOddsRangeStatsList() {
            // Return in order, sorted by ROI descending
            return oddsRangeStats.values().stream()
                .filter(ors -> ors.totalBets > 0) // Only show ranges with bets
                .sorted((a, b) -> Double.compare(b.roi, a.roi))
                .collect(Collectors.toList());
        }
    }
    
    public static class ModelStats {
        public String modelId;
        public String modelName;
        public int totalPredictions;
        public int totalVerified;
        public int correct;
        public int incorrect;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        // Per-odds-range stats for this model
        public Map<String, OddsRangeStats> oddsRangeStats = new LinkedHashMap<>();
        
        public ModelStats() {
            // Initialize odds range buckets
            oddsRangeStats.put("1.0-1.5", new OddsRangeStats("1.0-1.5", 1.0, 1.5));
            oddsRangeStats.put("1.5-2.0", new OddsRangeStats("1.5-2.0", 1.5, 2.0));
            oddsRangeStats.put("2.0-2.5", new OddsRangeStats("2.0-2.5", 2.0, 2.5));
            oddsRangeStats.put("2.5-3.0", new OddsRangeStats("2.5-3.0", 2.5, 3.0));
            oddsRangeStats.put("3.0-4.0", new OddsRangeStats("3.0-4.0", 3.0, 4.0));
            oddsRangeStats.put("4.0+", new OddsRangeStats("4.0+", 4.0, 100.0));
        }
        
        public void addOddsRangeBet(double odds, boolean isCorrect) {
            for (OddsRangeStats ors : oddsRangeStats.values()) {
                if (odds >= ors.minOdds && odds < ors.maxOdds) {
                    ors.addBet(isCorrect, odds);
                    break;
                }
            }
        }
        
        public void calculate() {
            accuracy = totalVerified > 0 ? (double) correct / totalVerified : 0;
            profit = totalReturn - totalStaked;
            roi = totalStaked > 0 ? (profit / totalStaked) * 100 : 0;
            
            // Calculate odds range stats
            for (OddsRangeStats ors : oddsRangeStats.values()) {
                ors.calculate();
            }
        }
        
        // Getters for Thymeleaf
        public String getModelId() { return modelId; }
        public String getModelName() { return modelName; }
        public int getTotalPredictions() { return totalPredictions; }
        public int getTotalVerified() { return totalVerified; }
        public int getCorrect() { return correct; }
        public int getIncorrect() { return incorrect; }
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getTotalStakedFormatted() { return String.format("$%.0f", totalStaked); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
        public boolean isProfitable() { return profit > 0; }
        
        public List<OddsRangeStats> getOddsRangeStatsList() {
            return oddsRangeStats.values().stream()
                .filter(ors -> ors.totalBets > 0)
                .sorted((a, b) -> Double.compare(b.roi, a.roi))
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Odds range statistics for analyzing profitable betting ranges.
     */
    public static class OddsRangeStats {
        public String range;
        public double minOdds;
        public double maxOdds;
        public int totalBets;
        public int correct;
        public int incorrect;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        public OddsRangeStats(String range, double minOdds, double maxOdds) {
            this.range = range;
            this.minOdds = minOdds;
            this.maxOdds = maxOdds;
        }
        
        public void addBet(boolean isCorrect, double odds) {
            totalBets++;
            totalStaked += 1.0;
            if (isCorrect) {
                correct++;
                totalReturn += odds;
            } else {
                incorrect++;
            }
        }
        
        public void calculate() {
            accuracy = totalBets > 0 ? (double) correct / totalBets : 0;
            profit = totalReturn - totalStaked;
            roi = totalStaked > 0 ? (profit / totalStaked) * 100 : 0;
        }
        
        // Getters for Thymeleaf
        public String getRange() { return range; }
        public int getTotalBets() { return totalBets; }
        public int getCorrect() { return correct; }
        public int getIncorrect() { return incorrect; }
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getTotalStakedFormatted() { return String.format("$%.0f", totalStaked); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
        public boolean isProfitable() { return profit > 0; }
    }

    /**
     * Generate predictions for all models with odds data (by days ahead - legacy).
     */
    private List<MultiModelPrediction> generateMultiModelPredictions(int days, Double minOdds, Double maxOdds) {
        return generateMultiModelPredictions(LocalDate.now(), LocalDate.now().plusDays(days), minOdds, maxOdds, null);
    }

    /**
     * Generate predictions for all models with odds data (by date range).
     */
    private List<MultiModelPrediction> generateMultiModelPredictions(LocalDate startDate, LocalDate endDate, Double minOdds, Double maxOdds) {
        return generateMultiModelPredictions(startDate, endDate, minOdds, maxOdds, null);
    }

    /**
     * Generate predictions for selected models with odds data (by date range).
     * @param modelIds Optional list of model IDs to filter by. If null or empty, all models are used.
     */
    private List<MultiModelPrediction> generateMultiModelPredictions(LocalDate startDate, LocalDate endDate, Double minOdds, Double maxOdds, List<String> modelIds) {
        List<FixtureDocument> upcoming = predictionService.getUnfinishedMatches(startDate, endDate);
        List<ModelConfigDocument> models = modelService.getAllModels();
        
        // Filter models if specific IDs are provided
        if (modelIds != null && !modelIds.isEmpty()) {
            Set<String> selectedModelIds = new HashSet<>(modelIds);
            models = models.stream()
                    .filter(m -> selectedModelIds.contains(m.getModelId()))
                    .collect(Collectors.toList());
        }
        
        // Pre-fetch odds for all matches
        List<String> matchKeys = upcoming.stream()
                .map(FixtureDocument::getEventKey)
                .collect(Collectors.toList());
        Map<String, OddsDocument> oddsMap = oddsRepository.findByMatchKeyIn(matchKeys).stream()
                .collect(Collectors.toMap(OddsDocument::getMatchKey, o -> o, (a, b) -> a));
        
        List<MultiModelPrediction> allPredictions = new ArrayList<>();
        
        for (FixtureDocument fixture : upcoming) {
            OddsDocument odds = oddsMap.get(fixture.getEventKey());
            
            for (ModelConfigDocument modelConfig : models) {
                try {
                    PredictionDocument pred = predictionService.predict(fixture.getEventKey(), modelConfig.getModelId());
                    
                    MultiModelPrediction mmp = new MultiModelPrediction();
                    mmp.matchKey = fixture.getEventKey();
                    mmp.matchDate = fixture.getEventDate();
                    mmp.tournamentKey = fixture.getTournamentKey();
                    mmp.player1Key = pred.getPlayer1Key();
                    mmp.player1Name = pred.getPlayer1Name();
                    mmp.player2Key = pred.getPlayer2Key();
                    mmp.player2Name = pred.getPlayer2Name();
                    mmp.modelId = modelConfig.getModelId();
                    mmp.modelName = modelConfig.getName();
                    mmp.predictedWinnerKey = pred.getPredictedWinner();
                    mmp.predictedWinnerName = pred.getPredictedWinner().equals(pred.getPlayer1Key()) 
                        ? pred.getPlayer1Name() : pred.getPlayer2Name();
                    mmp.winProbability = pred.getPredictedWinner().equals(pred.getPlayer1Key())
                        ? pred.getPlayer1WinProbability() : pred.getPlayer2WinProbability();
                    mmp.confidence = pred.getConfidence();
                    mmp.factorScores = pred.getFactorScores();
                    
                    // Add odds
                    if (odds != null) {
                        mmp.player1Odds = odds.getHomeOdds();
                        mmp.player2Odds = odds.getAwayOdds();
                        mmp.predictedWinnerOdds = pred.getPredictedWinner().equals(pred.getPlayer1Key())
                            ? odds.getHomeOdds() : odds.getAwayOdds();
                    }
                    
                    // Apply odds range filter
                    if (mmp.predictedWinnerOdds == null) {
                        if (minOdds != null || maxOdds != null) continue;
                    } else {
                        if (minOdds != null && minOdds > 1.0 && mmp.predictedWinnerOdds < minOdds) continue;
                        if (maxOdds != null && mmp.predictedWinnerOdds > maxOdds) continue;
                    }
                    
                    allPredictions.add(mmp);
                } catch (Exception e) {
                    // Skip failed predictions
                }
            }
        }
        
        // Sort by match date, then by model
        allPredictions.sort((a, b) -> {
            int dateCompare = a.matchDate != null && b.matchDate != null 
                ? a.matchDate.compareTo(b.matchDate) : 0;
            if (dateCompare != 0) return dateCompare;
            return a.modelName.compareTo(b.modelName);
        });
        
        return allPredictions;
    }
    
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * DTO for multi-model prediction results
     */
    public static class MultiModelPrediction {
        public String matchKey;
        public LocalDate matchDate;
        public String tournamentKey;
        public String player1Key;
        public String player1Name;
        public String player2Key;
        public String player2Name;
        public String modelId;
        public String modelName;
        public String predictedWinnerKey;
        public String predictedWinnerName;
        public double winProbability;
        public double confidence;
        public Map<String, Double> factorScores;
        public Double player1Odds;
        public Double player2Odds;
        public Double predictedWinnerOdds;
        
        // Verification fields
        public String actualWinnerKey;
        public String actualWinnerName;
        public String actualScore;
        public String status;
        public Boolean isCorrect;
        public Double profit;
        
        // Getters for Thymeleaf
        public String getMatchKey() { return matchKey; }
        public LocalDate getMatchDate() { return matchDate; }
        public String getTournamentKey() { return tournamentKey; }
        public String getPlayer1Key() { return player1Key; }
        public String getPlayer1Name() { return player1Name; }
        public String getPlayer2Key() { return player2Key; }
        public String getPlayer2Name() { return player2Name; }
        public String getModelId() { return modelId; }
        public String getModelName() { return modelName; }
        public String getPredictedWinnerKey() { return predictedWinnerKey; }
        public String getPredictedWinnerName() { return predictedWinnerName; }
        public double getWinProbability() { return winProbability; }
        public double getConfidence() { return confidence; }
        public Map<String, Double> getFactorScores() { return factorScores; }
        public Double getPlayer1Odds() { return player1Odds; }
        public Double getPlayer2Odds() { return player2Odds; }
        public Double getPredictedWinnerOdds() { return predictedWinnerOdds; }
        
        public String getWinProbabilityFormatted() {
            return String.format("%.1f%%", winProbability * 100);
        }
        public String getConfidenceFormatted() {
            return String.format("%.1f%%", confidence * 100);
        }
        public String getPlayer1OddsFormatted() {
            return player1Odds != null ? String.format("%.2f", player1Odds) : "-";
        }
        public String getPlayer2OddsFormatted() {
            return player2Odds != null ? String.format("%.2f", player2Odds) : "-";
        }
        public String getPredictedWinnerOddsFormatted() {
            return predictedWinnerOdds != null ? String.format("%.2f", predictedWinnerOdds) : "-";
        }
        public String getPotentialProfitFormatted() {
            return predictedWinnerOdds != null ? String.format("+%.2f", predictedWinnerOdds - 1.0) : "-";
        }
        
        // Verification getters
        public String getActualWinnerKey() { return actualWinnerKey; }
        public String getActualWinnerName() { return actualWinnerName; }
        public String getActualScore() { return actualScore; }
        public String getStatus() { return status; }
        public Boolean getIsCorrect() { return isCorrect; }
        public Double getProfit() { return profit; }
        public String getProfitFormatted() {
            return profit != null ? String.format("%+.2f", profit) : "-";
        }
        public boolean isVerified() {
            return status != null && !"Not Started".equalsIgnoreCase(status);
        }
    }

    @GetMapping("/predictions")
    public String predictions(Model model) {
        List<PredictionDocument> allPredictions = predictionRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getMatchDate() == null) return 1;
                    if (b.getMatchDate() == null) return -1;
                    return b.getMatchDate().compareTo(a.getMatchDate());
                })
                .collect(Collectors.toList());

        model.addAttribute("predictions", allPredictions);
        model.addAttribute("predictionCount", allPredictions.size());

        return "predictions";
    }

    @GetMapping("/backtest")
    public String backtest(Model model) {
        model.addAttribute("models", modelService.getAllModels());
        model.addAttribute("minOdds", null); // No filter by default
        return "backtest";
    }

    @PostMapping("/backtest")
    public String runBacktest(
            @RequestParam String modelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double minOdds,
            Model model
    ) {
        // Convert minOdds of 0 or 1.0 to null (no filter)
        Double effectiveMinOdds = (minOdds == null || minOdds <= 1.0) ? null : minOdds;
        
        BacktestService.BacktestResult result = backtestService.runBacktest(modelId, startDate, endDate, effectiveMinOdds);
        model.addAttribute("result", result);
        model.addAttribute("selectedModel", modelId);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("minOdds", minOdds);

        // Get confidence analysis
        Map<String, BacktestService.ConfidenceBucket> confidenceAnalysis = backtestService.analyzeByConfidence(modelId);
        model.addAttribute("confidenceAnalysis", confidenceAnalysis);

        model.addAttribute("models", modelService.getAllModels());
        return "backtest";
    }

    @GetMapping("/models")
    public String models(Model model) {
        model.addAttribute("models", modelService.getAllModels());
        model.addAttribute("activeModel", modelService.getActiveModel());
        return "models";
    }

    @PostMapping("/models/activate")
    public String activateModel(@RequestParam String modelId, Model model) {
        modelService.setActiveModel(modelId);
        return "redirect:/ui/models";
    }

    @PostMapping("/models/create")
    public String createModel(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam double ranking,
            @RequestParam double h2h,
            @RequestParam double recentForm,
            @RequestParam double surfaceForm,
            @RequestParam double fatigue,
            @RequestParam double momentum,
            @RequestParam double oaps,
            @RequestParam double owlRating,
            @RequestParam double owlMomentum,
            Model model
    ) {
        try {
            // Generate a unique model ID from the name
            String modelId = "custom-" + name.toLowerCase().replaceAll("[^a-z0-9]+", "-");
            
            // Normalize weights to sum to 1.0
            double total = ranking + h2h + recentForm + surfaceForm + fatigue + momentum + oaps + owlRating + owlMomentum;
            
            Map<String, Double> weights = new LinkedHashMap<>();
            weights.put("ranking", ranking / total);
            weights.put("h2h", h2h / total);
            weights.put("recentForm", recentForm / total);
            weights.put("surfaceForm", surfaceForm / total);
            weights.put("fatigue", fatigue / total);
            weights.put("momentum", momentum / total);
            weights.put("oddsAdjustedPerformance", oaps / total);
            weights.put("owlRating", owlRating / total);
            weights.put("owlMomentum", owlMomentum / total);
            
            modelService.createCustomModel(modelId, name, description != null ? description : "", weights);
            
            return "redirect:/ui/models?success=created";
        } catch (Exception e) {
            log.error("Failed to create model: {}", e.getMessage());
            return "redirect:/ui/models?error=" + e.getMessage();
        }
    }

    @PostMapping("/models/delete")
    public String deleteModel(@RequestParam String modelId, Model model) {
        try {
            boolean deleted = modelService.deleteModel(modelId);
            if (deleted) {
                return "redirect:/ui/models?success=deleted";
            } else {
                return "redirect:/ui/models?error=Model+not+found";
            }
        } catch (IllegalArgumentException e) {
            return "redirect:/ui/models?error=" + e.getMessage().replace(" ", "+");
        }
    }

    // ============ CLEAR PREDICTIONS ============

    @PostMapping("/predictions/clear")
    public String clearPredictions(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String modelId
    ) {
        long predictionsDeleted = 0;
        long resultsDeleted = 0;
        
        if ("model".equals(scope) && modelId != null && !modelId.isBlank()) {
            // Clear predictions for a specific model
            List<PredictionDocument> modelPredictions = predictionRepository.findByModelId(modelId);
            predictionsDeleted = modelPredictions.size();
            predictionRepository.deleteAll(modelPredictions);
            
            List<com.tennis.prediction.model.PredictionResultDocument> modelResults = resultRepository.findByModelId(modelId);
            resultsDeleted = modelResults.size();
            resultRepository.deleteAll(modelResults);
            
            log.info("Cleared {} predictions and {} results for model: {}", predictionsDeleted, resultsDeleted, modelId);
        } else {
            // Clear all predictions
            predictionsDeleted = predictionRepository.count();
            resultsDeleted = resultRepository.count();
            predictionRepository.deleteAll();
            resultRepository.deleteAll();
            
            log.info("Cleared ALL {} predictions and {} results", predictionsDeleted, resultsDeleted);
        }
        
        return "redirect:/ui/dashboard?cleared=" + predictionsDeleted + "&results=" + resultsDeleted;
    }

    // ============ OWL RATING SYSTEM ============

    /**
     * OWL Rating Leaderboard
     */
    @GetMapping("/owl")
    public String owlLeaderboard(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "5") int minMatches,
            @RequestParam(required = false) String search,
            Model model
    ) {
        List<OwlRatingDocument> leaderboard;
        
        if (search != null && !search.isBlank()) {
            leaderboard = owlRatingService.searchPlayers(search);
            model.addAttribute("searchQuery", search);
        } else {
            leaderboard = owlRatingService.getLeaderboard(limit, minMatches);
        }
        
        model.addAttribute("leaderboard", leaderboard);
        model.addAttribute("stats", owlRatingService.getStats());
        model.addAttribute("limit", limit);
        model.addAttribute("minMatches", minMatches);
        
        return "owl-leaderboard";
    }

    /**
     * OWL Player Detail Page
     */
    @GetMapping("/owl/player/{playerKey}")
    public String owlPlayerDetail(@PathVariable String playerKey, Model model) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingService.getPlayerRating(playerKey);
        
        if (ratingOpt.isEmpty()) {
            model.addAttribute("error", "Player not found: " + playerKey);
            return "error";
        }
        
        OwlRatingDocument rating = ratingOpt.get();
        model.addAttribute("player", rating);
        model.addAttribute("recentChanges", rating.getRecentChanges());
        
        return "owl-player";
    }

    /**
     * OWL Admin - Initialize ratings page
     */
    @GetMapping("/owl/admin")
    public String owlAdmin(Model model) {
        model.addAttribute("stats", owlRatingService.getStats());
        model.addAttribute("totalRatings", owlRatingRepository.count());
        
        // Default date range
        model.addAttribute("startDate", LocalDate.now().minusMonths(3));
        model.addAttribute("endDate", LocalDate.now());
        
        return "owl-admin";
    }

    /**
     * OWL Admin - Initialize ratings from history
     */
    @PostMapping("/owl/admin/initialize")
    public String owlInitialize(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Model model
    ) {
        OwlRatingService.InitializationResult result = owlRatingService.initializeFromHistory(startDate, endDate);
        
        model.addAttribute("initResult", result);
        model.addAttribute("stats", owlRatingService.getStats());
        model.addAttribute("totalRatings", owlRatingRepository.count());
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        
        return "owl-admin";
    }

    /**
     * Compare two players' OWL ratings
     */
    @GetMapping("/owl/compare")
    public String owlCompare(
            @RequestParam(required = false) String player1,
            @RequestParam(required = false) String player2,
            Model model
    ) {
        if (player1 != null && player2 != null) {
            owlRatingService.getPlayerRating(player1).ifPresent(r -> model.addAttribute("rating1", r));
            owlRatingService.getPlayerRating(player2).ifPresent(r -> model.addAttribute("rating2", r));
        }
        
        return "owl-compare";
    }

    /**
     * OWL Trends Dashboard - Shows hot/cold players, momentum, consistency
     */
    @GetMapping("/owl/trends")
    public String owlTrends(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "5") int minMatches,
            Model model
    ) {
        // Get trend stats
        OwlRatingService.TrendStats trendStats = owlRatingService.getTrendStats(minMatches);
        model.addAttribute("trendStats", trendStats);
        
        // Hottest players (highest momentum)
        model.addAttribute("hottestPlayers", owlRatingService.getHottestPlayers(limit, minMatches));
        
        // Coldest players (lowest momentum)
        model.addAttribute("coldestPlayers", owlRatingService.getColdestPlayers(limit, minMatches));
        
        // Most consistent players
        model.addAttribute("consistentPlayers", owlRatingService.getMostConsistentPlayers(limit, minMatches));
        
        // Most volatile/wild card players
        model.addAttribute("volatilePlayers", owlRatingService.getMostVolatilePlayers(limit, minMatches));
        
        // Rising stars (low ATP rank but high momentum)
        model.addAttribute("risingStars", owlRatingService.getRisingStars(limit, 100, minMatches));
        
        model.addAttribute("limit", limit);
        model.addAttribute("minMatches", minMatches);
        
        return "owl-trends";
    }

    /**
     * API endpoint for player rating history chart data (JSON)
     */
    @GetMapping("/owl/player/{playerKey}/chart-data")
    @ResponseBody
    public Map<String, Object> getPlayerChartData(@PathVariable String playerKey) {
        List<OwlRatingService.RatingHistoryPoint> history = owlRatingService.getPlayerRatingHistory(playerKey);
        
        List<String> labels = new ArrayList<>();
        List<Double> ratings = new ArrayList<>();
        List<String> colors = new ArrayList<>();
        List<String> tooltips = new ArrayList<>();
        
        for (OwlRatingService.RatingHistoryPoint point : history) {
            labels.add(point.getDateFormatted());
            ratings.add(point.rating());
            colors.add(point.won() ? "#22c55e" : (point.opponentName() == null ? "#6b7280" : "#ef4444"));
            tooltips.add(point.getLabel() + (point.tournamentName() != null ? " @ " + point.tournamentName() : ""));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("ratings", ratings);
        result.put("colors", colors);
        result.put("tooltips", tooltips);
        
        return result;
    }
}

