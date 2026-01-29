package com.tennis.adapter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tennis.adapter.client.ApiTennisClient;
import com.tennis.adapter.model.FixtureDocument;
import com.tennis.adapter.repository.FixtureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for verifying predictions against actual match results.
 * Can optionally fetch fresh data from API Tennis for matches not in the database.
 */
@Service
public class PredictionVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PredictionVerificationService.class);

    private final FixtureRepository fixtureRepository;
    private final ApiTennisClient apiClient;

    public PredictionVerificationService(FixtureRepository fixtureRepository, ApiTennisClient apiClient) {
        this.fixtureRepository = fixtureRepository;
        this.apiClient = apiClient;
    }

    /**
     * Parse and verify predictions from an uploaded CSV file.
     * Uses only database data (no API calls).
     */
    public VerificationReport verifyPredictions(MultipartFile csvFile) throws IOException {
        return verifyPredictions(csvFile, false);
    }

    /**
     * Parse and verify predictions from an uploaded CSV file.
     * @param fetchFromApi If true, fetch fresh data from API Tennis for missing/unfinished matches
     */
    public VerificationReport verifyPredictions(MultipartFile csvFile, boolean fetchFromApi) throws IOException {
        List<PredictionRow> predictions = parseCsv(csvFile);
        return generateReport(predictions, fetchFromApi);
    }

    /**
     * Parse the CSV file into prediction rows.
     */
    private List<PredictionRow> parseCsv(MultipartFile csvFile) throws IOException {
        List<PredictionRow> predictions = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            
            // Parse header to get column indices
            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim().toLowerCase().replace(" ", "_"), i);
            }
            
            // Validate required columns
            validateRequiredColumns(columnIndex);
            
            String line;
            int lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;
                
                try {
                    String[] values = parseCSVLine(line);
                    PredictionRow row = new PredictionRow();
                    
                    row.matchKey = getColumn(values, columnIndex, "match_key");
                    row.matchDate = parseDate(getColumn(values, columnIndex, "match_date"));
                    row.tournamentKey = getColumn(values, columnIndex, "tournament_key");
                    row.player1Key = getColumn(values, columnIndex, "player_1_key");
                    row.player1Name = getColumn(values, columnIndex, "player_1_name");
                    row.player2Key = getColumn(values, columnIndex, "player_2_key");
                    row.player2Name = getColumn(values, columnIndex, "player_2_name");
                    row.modelId = getColumn(values, columnIndex, "model_id");
                    row.modelName = getColumn(values, columnIndex, "model_name");
                    row.predictedWinnerKey = getColumn(values, columnIndex, "predicted_winner_key");
                    row.predictedWinnerName = getColumn(values, columnIndex, "predicted_winner_name");
                    row.winProbability = parseDouble(getColumn(values, columnIndex, "win_probability"));
                    row.confidence = parseDouble(getColumn(values, columnIndex, "confidence"));
                    row.player1Odds = parseDouble(getColumn(values, columnIndex, "player_1_odds"));
                    row.player2Odds = parseDouble(getColumn(values, columnIndex, "player_2_odds"));
                    row.predictedWinnerOdds = parseDouble(getColumn(values, columnIndex, "predicted_winner_odds"));
                    
                    predictions.add(row);
                } catch (Exception e) {
                    log.warn("Failed to parse line {}: {}", lineNum, e.getMessage());
                }
            }
        }
        
        log.info("Parsed {} predictions from CSV", predictions.size());
        return predictions;
    }

    /**
     * Generate verification report by checking predictions against actual results.
     */
    private VerificationReport generateReport(List<PredictionRow> predictions, boolean fetchFromApi) {
        // Get unique match keys
        Set<String> matchKeys = predictions.stream()
                .map(p -> p.matchKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        // Fetch results from database
        Map<String, FixtureDocument> fixtures = fixtureRepository.findByEventKeyIn(new ArrayList<>(matchKeys)).stream()
                .collect(Collectors.toMap(FixtureDocument::getEventKey, f -> f, (a, b) -> a));
        
        log.info("Found {} fixtures in database for {} match keys", fixtures.size(), matchKeys.size());
        
        // Track API fetch stats
        int apiFetchAttempts = 0;
        int apiFetchSuccess = 0;
        int apiFetchSaved = 0;
        
        // If fetchFromApi is enabled, fetch missing/unfinished matches
        if (fetchFromApi) {
            // Find matches that need API fetch
            Set<String> needsFetch = new HashSet<>();
            for (String matchKey : matchKeys) {
                FixtureDocument fixture = fixtures.get(matchKey);
                if (fixture == null || !"Finished".equalsIgnoreCase(fixture.getStatus())) {
                    needsFetch.add(matchKey);
                }
            }
            
            if (!needsFetch.isEmpty()) {
                log.info("Fetching {} matches from API Tennis...", needsFetch.size());
                
                // Group by date for efficient batching
                Map<LocalDate, List<String>> byDate = new HashMap<>();
                for (PredictionRow pred : predictions) {
                    if (pred.matchKey != null && needsFetch.contains(pred.matchKey) && pred.matchDate != null) {
                        byDate.computeIfAbsent(pred.matchDate, k -> new ArrayList<>()).add(pred.matchKey);
                    }
                }
                
                // Fetch by date range (grouped)
                for (Map.Entry<LocalDate, List<String>> entry : byDate.entrySet()) {
                    LocalDate date = entry.getKey();
                    try {
                        apiFetchAttempts++;
                        log.info("Fetching fixtures for date: {}", date);
                        
                        JsonNode response = apiClient.getFixturesByDateRange(date.toString(), date.toString());
                        
                        if (response != null && response.has("success") && response.get("success").asInt() == 1) {
                            JsonNode result = response.get("result");
                            if (result != null && result.isArray()) {
                                for (JsonNode node : result) {
                                    String eventKey = node.has("event_key") ? node.get("event_key").asText() : null;
                                    if (eventKey != null && needsFetch.contains(eventKey)) {
                                        // Parse fixture from API
                                        FixtureDocument apiDoc = parseFixtureFromApi(node);
                                        if (apiDoc != null) {
                                            apiFetchSuccess++;
                                            
                                            // Check if fixture already exists in DB to get its ID
                                            Optional<FixtureDocument> existingOpt = fixtureRepository.findByEventKey(eventKey);
                                            if (existingOpt.isPresent()) {
                                                // Update existing document
                                                FixtureDocument existing = existingOpt.get();
                                                existing.setStatus(apiDoc.getStatus());
                                                existing.setScore(apiDoc.getScore());
                                                existing.setWinner(apiDoc.getWinner());
                                                existing.setRaw(apiDoc.getRaw());
                                                fixtureRepository.save(existing);
                                                fixtures.put(eventKey, existing);
                                                apiFetchSaved++;
                                                log.debug("Updated fixture {} from API: {} vs {} - status: {}", 
                                                        eventKey, existing.getFirstPlayerName(), existing.getSecondPlayerName(), existing.getStatus());
                                            } else {
                                                // New fixture - save it
                                                fixtureRepository.save(apiDoc);
                                                fixtures.put(eventKey, apiDoc);
                                                apiFetchSaved++;
                                                log.debug("Saved new fixture {} from API: {} vs {}", 
                                                        eventKey, apiDoc.getFirstPlayerName(), apiDoc.getSecondPlayerName());
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch fixtures for date {}: {}", date, e.getMessage());
                    }
                }
                
                log.info("API fetch complete: {} attempts, {} fixtures found, {} saved", 
                        apiFetchAttempts, apiFetchSuccess, apiFetchSaved);
            }
        }
        
        // Process each prediction
        List<VerifiedPrediction> verifiedPredictions = new ArrayList<>();
        Map<String, ModelStats> modelStats = new HashMap<>();
        Map<String, OddsRangeStats> oddsRangeStats = new LinkedHashMap<>();
        
        // Track odds ranges per model: modelId -> (range -> stats)
        Map<String, Map<String, OddsRangeStats>> oddsRangeStatsByModel = new LinkedHashMap<>();
        
        // Initialize odds ranges (aggregate)
        oddsRangeStats.put("< 1.50", new OddsRangeStats("< 1.50"));
        oddsRangeStats.put("1.50 - 2.00", new OddsRangeStats("1.50 - 2.00"));
        oddsRangeStats.put("2.00 - 3.00", new OddsRangeStats("2.00 - 3.00"));
        oddsRangeStats.put("> 3.00", new OddsRangeStats("> 3.00"));
        
        int totalPredictions = 0;
        int matchesWithResults = 0;
        int correct = 0;
        int incorrect = 0;
        double totalStaked = 0;
        double totalReturn = 0;
        
        for (PredictionRow pred : predictions) {
            totalPredictions++;
            
            FixtureDocument fixture = fixtures.get(pred.matchKey);
            VerifiedPrediction vp = new VerifiedPrediction();
            vp.prediction = pred;
            
            if (fixture == null) {
                vp.status = "NOT_FOUND";
                verifiedPredictions.add(vp);
                continue;
            }
            
            vp.actualStatus = fixture.getStatus();
            
            if (!"Finished".equalsIgnoreCase(fixture.getStatus())) {
                vp.status = "NOT_FINISHED";
                verifiedPredictions.add(vp);
                continue;
            }
            
            matchesWithResults++;
            
            // Determine actual winner
            String actualWinnerKey = determineWinnerKey(fixture);
            vp.actualWinnerKey = actualWinnerKey;
            vp.actualWinnerName = actualWinnerKey != null && actualWinnerKey.equals(fixture.getFirstPlayerKey())
                    ? fixture.getFirstPlayerName() : fixture.getSecondPlayerName();
            vp.actualScore = fixture.getScore();
            
            // Check if prediction was correct
            boolean isCorrect = pred.predictedWinnerKey != null && pred.predictedWinnerKey.equals(actualWinnerKey);
            vp.isCorrect = isCorrect;
            vp.status = isCorrect ? "CORRECT" : "INCORRECT";
            
            if (isCorrect) {
                correct++;
            } else {
                incorrect++;
            }
            
            // Calculate profit/loss
            if (pred.predictedWinnerOdds != null && pred.predictedWinnerOdds > 0) {
                totalStaked += 1.0;
                if (isCorrect) {
                    vp.profit = pred.predictedWinnerOdds - 1.0;
                    totalReturn += pred.predictedWinnerOdds;
                } else {
                    vp.profit = -1.0;
                }
            }
            
            // Update model stats
            ModelStats ms = modelStats.computeIfAbsent(pred.modelId, k -> new ModelStats(pred.modelId, pred.modelName));
            ms.addResult(isCorrect, pred.predictedWinnerOdds);
            
            // Update odds range stats (aggregate)
            if (pred.predictedWinnerOdds != null) {
                String range = getOddsRange(pred.predictedWinnerOdds);
                OddsRangeStats ors = oddsRangeStats.get(range);
                if (ors != null) {
                    ors.addResult(isCorrect, pred.predictedWinnerOdds);
                }
                
                // Update per-model odds range stats
                Map<String, OddsRangeStats> modelRanges = oddsRangeStatsByModel.computeIfAbsent(
                        pred.modelId, 
                        k -> {
                            Map<String, OddsRangeStats> m = new LinkedHashMap<>();
                            m.put("< 1.50", new OddsRangeStats("< 1.50"));
                            m.put("1.50 - 2.00", new OddsRangeStats("1.50 - 2.00"));
                            m.put("2.00 - 3.00", new OddsRangeStats("2.00 - 3.00"));
                            m.put("> 3.00", new OddsRangeStats("> 3.00"));
                            return m;
                        });
                OddsRangeStats modelOrs = modelRanges.get(range);
                if (modelOrs != null) {
                    modelOrs.addResult(isCorrect, pred.predictedWinnerOdds);
                }
            }
            
            verifiedPredictions.add(vp);
        }
        
        // Build report
        VerificationReport report = new VerificationReport();
        report.totalPredictions = totalPredictions;
        report.matchesWithResults = matchesWithResults;
        report.matchesNotFound = (int) verifiedPredictions.stream().filter(v -> "NOT_FOUND".equals(v.status)).count();
        report.matchesNotFinished = (int) verifiedPredictions.stream().filter(v -> "NOT_FINISHED".equals(v.status)).count();
        report.correct = correct;
        report.incorrect = incorrect;
        report.accuracy = matchesWithResults > 0 ? (double) correct / matchesWithResults : 0;
        report.totalStaked = totalStaked;
        report.totalReturn = totalReturn;
        report.profit = totalReturn - totalStaked;
        report.roi = totalStaked > 0 ? (report.profit / totalStaked) * 100 : 0;
        
        // API fetch stats
        report.apiFetchAttempts = apiFetchAttempts;
        report.apiFetchSuccess = apiFetchSuccess;
        report.apiFetchSaved = apiFetchSaved;
        report.fetchedFromApi = fetchFromApi;
        
        report.verifiedPredictions = verifiedPredictions;
        report.modelStats = new ArrayList<>(modelStats.values());
        report.modelStats.sort((a, b) -> Double.compare(b.roi, a.roi)); // Sort by ROI desc
        report.oddsRangeStats = new ArrayList<>(oddsRangeStats.values());
        
        // Build per-model odds range stats
        report.oddsRangeStatsByModel = new ArrayList<>();
        for (Map.Entry<String, Map<String, OddsRangeStats>> entry : oddsRangeStatsByModel.entrySet()) {
            String modelId = entry.getKey();
            // Find model name
            String modelName = modelId;
            for (ModelStats ms : report.modelStats) {
                if (ms.modelId.equals(modelId)) {
                    modelName = ms.modelName;
                    break;
                }
            }
            ModelOddsRangeStats mors = new ModelOddsRangeStats(modelId, modelName);
            mors.rangeStats = new ArrayList<>(entry.getValue().values());
            report.oddsRangeStatsByModel.add(mors);
        }
        // Sort by model name
        report.oddsRangeStatsByModel.sort((a, b) -> a.modelName.compareTo(b.modelName));
        
        // Find best model
        if (!report.modelStats.isEmpty()) {
            report.bestModel = report.modelStats.get(0);
        }
        
        return report;
    }

    /**
     * Parse a fixture from API JSON response.
     */
    private FixtureDocument parseFixtureFromApi(JsonNode node) {
        try {
            FixtureDocument doc = new FixtureDocument();
            
            doc.setEventKey(getTextOrNull(node, "event_key"));
            doc.setTournamentKey(getTextOrNull(node, "tournament_key"));
            doc.setEventTypeKey(getTextOrNull(node, "event_type_type"));
            
            String dateStr = getTextOrNull(node, "event_date");
            if (dateStr != null && !dateStr.isEmpty()) {
                doc.setEventDate(LocalDate.parse(dateStr));
            }
            
            doc.setFirstPlayerKey(getTextOrNull(node, "event_first_player"));
            doc.setSecondPlayerKey(getTextOrNull(node, "event_second_player"));
            doc.setFirstPlayerName(getTextOrNull(node, "event_home_team"));
            doc.setSecondPlayerName(getTextOrNull(node, "event_away_team"));
            doc.setStatus(getTextOrNull(node, "event_status"));
            doc.setScore(getTextOrNull(node, "event_final_result"));
            doc.setWinner(getTextOrNull(node, "event_winner"));
            
            // Store raw data
            org.bson.Document rawDoc = org.bson.Document.parse(node.toString());
            doc.setRaw(rawDoc);
            
            return doc;
        } catch (Exception e) {
            log.warn("Failed to parse fixture from API: {}", e.getMessage());
            return null;
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        return value.asText();
    }

    private String determineWinnerKey(FixtureDocument fixture) {
        String winner = fixture.getWinner();
        if (winner == null) return null;
        
        // Winner can be "First Player", "Second Player", or an actual player key
        if ("First Player".equalsIgnoreCase(winner) || winner.equals(fixture.getFirstPlayerKey())) {
            return fixture.getFirstPlayerKey();
        } else if ("Second Player".equalsIgnoreCase(winner) || winner.equals(fixture.getSecondPlayerKey())) {
            return fixture.getSecondPlayerKey();
        }
        
        return winner;
    }

    private String getOddsRange(double odds) {
        if (odds < 1.50) return "< 1.50";
        if (odds < 2.00) return "1.50 - 2.00";
        if (odds < 3.00) return "2.00 - 3.00";
        return "> 3.00";
    }

    private void validateRequiredColumns(Map<String, Integer> columnIndex) {
        List<String> required = Arrays.asList("match_key", "predicted_winner_key", "model_id");
        List<String> missing = required.stream()
                .filter(col -> !columnIndex.containsKey(col))
                .collect(Collectors.toList());
        
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required columns: " + missing + 
                    ". Available columns: " + columnIndex.keySet());
        }
    }

    private String getColumn(String[] values, Map<String, Integer> columnIndex, String columnName) {
        Integer idx = columnIndex.get(columnName);
        if (idx == null || idx >= values.length) return null;
        String val = values[idx].trim();
        return val.isEmpty() ? null : val;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Double.parseDouble(value.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a CSV line handling quoted values.
     */
    private String[] parseCSVLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString());
        
        return values.toArray(new String[0]);
    }

    // ============ DATA CLASSES ============

    public static class PredictionRow {
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
        public Double winProbability;
        public Double confidence;
        public Double player1Odds;
        public Double player2Odds;
        public Double predictedWinnerOdds;
    }

    public static class VerifiedPrediction {
        public PredictionRow prediction;
        public String status; // CORRECT, INCORRECT, NOT_FOUND, NOT_FINISHED
        public boolean isCorrect;
        public String actualWinnerKey;
        public String actualWinnerName;
        public String actualScore;
        public String actualStatus;
        public Double profit;
        
        public String getProfitFormatted() {
            if (profit == null) return "-";
            return String.format("%+.2f", profit);
        }
    }

    public static class ModelStats {
        public String modelId;
        public String modelName;
        public int bets;
        public int wins;
        public int losses;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        public ModelStats(String modelId, String modelName) {
            this.modelId = modelId;
            this.modelName = modelName != null ? modelName : modelId;
        }
        
        public void addResult(boolean won, Double odds) {
            bets++;
            if (won) {
                wins++;
                if (odds != null) {
                    totalReturn += odds;
                }
            } else {
                losses++;
            }
            if (odds != null) {
                totalStaked += 1.0;
            }
            
            accuracy = bets > 0 ? (double) wins / bets : 0;
            profit = totalReturn - totalStaked;
            roi = totalStaked > 0 ? (profit / totalStaked) * 100 : 0;
        }
        
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
    }

    public static class OddsRangeStats {
        public String range;
        public int bets;
        public int wins;
        public int losses;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        public OddsRangeStats(String range) {
            this.range = range;
        }
        
        public void addResult(boolean won, Double odds) {
            bets++;
            if (won) {
                wins++;
                if (odds != null) {
                    totalReturn += odds;
                }
            } else {
                losses++;
            }
            if (odds != null) {
                totalStaked += 1.0;
            }
            
            accuracy = bets > 0 ? (double) wins / bets : 0;
            profit = totalReturn - totalStaked;
            roi = totalStaked > 0 ? (profit / totalStaked) * 100 : 0;
        }
        
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
    }

    public static class VerificationReport {
        public int totalPredictions;
        public int matchesWithResults;
        public int matchesNotFound;
        public int matchesNotFinished;
        public int correct;
        public int incorrect;
        public double accuracy;
        public double totalStaked;
        public double totalReturn;
        public double profit;
        public double roi;
        
        // API fetch stats
        public boolean fetchedFromApi;
        public int apiFetchAttempts;
        public int apiFetchSuccess;
        public int apiFetchSaved;
        
        public List<VerifiedPrediction> verifiedPredictions;
        public List<ModelStats> modelStats;
        public List<OddsRangeStats> oddsRangeStats;
        public List<ModelOddsRangeStats> oddsRangeStatsByModel; // New: per-model breakdown
        public ModelStats bestModel;
        
        public String getAccuracyFormatted() { return String.format("%.1f%%", accuracy * 100); }
        public String getProfitFormatted() { return String.format("%+.2f", profit); }
        public String getRoiFormatted() { return String.format("%+.1f%%", roi); }
    }

    /**
     * Odds range stats grouped by model
     */
    public static class ModelOddsRangeStats {
        public String modelId;
        public String modelName;
        public List<OddsRangeStats> rangeStats;
        
        public ModelOddsRangeStats(String modelId, String modelName) {
            this.modelId = modelId;
            this.modelName = modelName;
            this.rangeStats = new ArrayList<>();
        }
    }
}
