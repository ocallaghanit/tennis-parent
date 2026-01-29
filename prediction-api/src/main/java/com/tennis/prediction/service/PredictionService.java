package com.tennis.prediction.service;

import com.tennis.prediction.model.ModelConfigDocument;
import com.tennis.prediction.model.OwlRatingDocument;
import com.tennis.prediction.model.PredictionDocument;
import com.tennis.prediction.model.readonly.*;
import com.tennis.prediction.repository.ModelConfigRepository;
import com.tennis.prediction.repository.OwlRatingRepository;
import com.tennis.prediction.repository.PredictionRepository;
import com.tennis.prediction.repository.readonly.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating match predictions using multiple factors.
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final FixtureReadRepository fixtureRepository;
    private final PlayerReadRepository playerRepository;
    private final TournamentReadRepository tournamentRepository;
    private final H2HReadRepository h2hRepository;
    private final OddsReadRepository oddsRepository;
    private final PredictionRepository predictionRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final OwlRatingRepository owlRatingRepository;
    private final OwlRatingService owlRatingService;

    // Default weights for prediction factors (includes OAPS and OWL factors)
    private static final Map<String, Double> DEFAULT_WEIGHTS;
    static {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ranking", 0.12);           // Reduced - OWL captures this dynamically
        weights.put("h2h", 0.15);
        weights.put("recentForm", 0.08);        // Reduced - OWL momentum is more accurate
        weights.put("surfaceForm", 0.10);
        weights.put("fatigue", 0.05);
        weights.put("momentum", 0.05);          // Reduced - OWL momentum is better
        weights.put("oddsAdjustedPerformance", 0.12);
        weights.put("owlRating", 0.18);         // NEW: OWL-based Elo score
        weights.put("owlMomentum", 0.15);       // NEW: OWL momentum/trend
        DEFAULT_WEIGHTS = Collections.unmodifiableMap(weights);
    }

    public PredictionService(
            FixtureReadRepository fixtureRepository,
            PlayerReadRepository playerRepository,
            TournamentReadRepository tournamentRepository,
            H2HReadRepository h2hRepository,
            OddsReadRepository oddsRepository,
            PredictionRepository predictionRepository,
            ModelConfigRepository modelConfigRepository,
            OwlRatingRepository owlRatingRepository,
            OwlRatingService owlRatingService
    ) {
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.tournamentRepository = tournamentRepository;
        this.h2hRepository = h2hRepository;
        this.oddsRepository = oddsRepository;
        this.predictionRepository = predictionRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.owlRatingRepository = owlRatingRepository;
        this.owlRatingService = owlRatingService;
    }

    /**
     * Generate a prediction for a match using the specified model.
     */
    public PredictionDocument predict(String matchKey, String modelId) {
        // Check for existing prediction
        Optional<PredictionDocument> existing = predictionRepository.findByMatchKeyAndModelId(matchKey, modelId);
        if (existing.isPresent()) {
            log.debug("Returning existing prediction for match {} with model {}", matchKey, modelId);
            return existing.get();
        }

        // Get fixture
        FixtureDocument fixture = fixtureRepository.findByEventKey(matchKey)
                .orElseThrow(() -> new IllegalArgumentException("Match not found: " + matchKey));

        // Get model config (or use defaults)
        Map<String, Double> weights = getModelWeights(modelId);

        // Generate prediction
        return generatePrediction(fixture, modelId, weights);
    }

    /**
     * Generate prediction for a fixture without saving (for backtesting).
     */
    public PredictionDocument generatePredictionForBacktest(FixtureDocument fixture, String modelId, LocalDate asOfDate) {
        Map<String, Double> weights = getModelWeights(modelId);
        return generatePredictionInternal(fixture, modelId, weights, asOfDate, false);
    }

    private PredictionDocument generatePrediction(FixtureDocument fixture, String modelId, Map<String, Double> weights) {
        PredictionDocument prediction = generatePredictionInternal(fixture, modelId, weights, fixture.getEventDate(), true);
        return predictionRepository.save(prediction);
    }

    private PredictionDocument generatePredictionInternal(
            FixtureDocument fixture,
            String modelId,
            Map<String, Double> weights,
            LocalDate asOfDate,
            boolean includeFuture
    ) {
        String p1Key = fixture.getFirstPlayerKey();
        String p2Key = fixture.getSecondPlayerKey();

        // Get player profiles
        PlayerDocument player1 = playerRepository.findByPlayerKey(p1Key).orElse(null);
        PlayerDocument player2 = playerRepository.findByPlayerKey(p2Key).orElse(null);

        // Get tournament for surface info
        TournamentDocument tournament = fixture.getTournamentKey() != null
                ? tournamentRepository.findByTournamentKey(fixture.getTournamentKey()).orElse(null)
                : null;
        String surface = tournament != null ? tournament.getSurface() : null;

        // Calculate factor scores (positive = favors player 1, negative = favors player 2)
        Map<String, Double> factorScores = new LinkedHashMap<>();

        // 1. Ranking Factor
        double rankingScore = calculateRankingScore(player1, player2);
        factorScores.put("ranking", rankingScore);

        // 2. Head-to-Head Factor
        double h2hScore = calculateH2HScore(p1Key, p2Key, asOfDate);
        factorScores.put("h2h", h2hScore);

        // 3. Recent Form Factor (last 10 matches)
        double formScore = calculateRecentFormScore(p1Key, p2Key, asOfDate);
        factorScores.put("recentForm", formScore);

        // 4. Surface Form Factor
        double surfaceScore = calculateSurfaceScore(player1, player2, surface);
        factorScores.put("surfaceForm", surfaceScore);

        // 5. Fatigue Factor (matches in last 14 days)
        double fatigueScore = calculateFatigueScore(p1Key, p2Key, asOfDate);
        factorScores.put("fatigue", fatigueScore);

        // 6. Momentum Factor (recent streak)
        double momentumScore = calculateMomentumScore(p1Key, p2Key, asOfDate);
        factorScores.put("momentum", momentumScore);

        // 7. Odds-Adjusted Performance Score (OAPS) - hidden momentum from market expectations
        double oapsScore = calculateOAPSScore(p1Key, p2Key, asOfDate);
        factorScores.put("oddsAdjustedPerformance", oapsScore);

        // 8. OWL Rating Factor - Elo-style probability from OWL ratings (point-in-time)
        double owlRatingScore = calculateOwlRatingScore(p1Key, p2Key, asOfDate);
        factorScores.put("owlRating", owlRatingScore);

        // 9. OWL Momentum Factor - Hot/Cold momentum from OWL system (point-in-time)
        double owlMomentumScore = calculateOwlMomentumScore(p1Key, p2Key, asOfDate);
        factorScores.put("owlMomentum", owlMomentumScore);

        // Calculate weighted total score
        double totalScore = 0.0;
        double totalWeight = 0.0;
        for (Map.Entry<String, Double> entry : factorScores.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 0.0);
            totalScore += entry.getValue() * weight;
            totalWeight += weight;
        }

        // Normalize to -1 to 1 range
        double normalizedScore = totalWeight > 0 ? totalScore / totalWeight : 0.0;

        // Convert to probabilities using sigmoid-like function
        double p1WinProb = 0.5 + (normalizedScore * 0.4); // Range: 0.1 to 0.9
        p1WinProb = Math.max(0.05, Math.min(0.95, p1WinProb)); // Clamp
        double p2WinProb = 1.0 - p1WinProb;

        // Calculate confidence based on factor agreement
        double confidence = calculateConfidence(factorScores, weights);

        // Adjust confidence based on OWL consistency (volatile players = less predictable) - point-in-time
        confidence = adjustConfidenceForOwlConsistency(confidence, p1Key, p2Key, asOfDate);

        // Create prediction document
        PredictionDocument prediction = new PredictionDocument();
        prediction.setMatchKey(fixture.getEventKey());
        prediction.setModelId(modelId);
        prediction.setMatchDate(fixture.getEventDate());
        prediction.setPlayer1Key(p1Key);
        prediction.setPlayer1Name(fixture.getFirstPlayerName());
        prediction.setPlayer2Key(p2Key);
        prediction.setPlayer2Name(fixture.getSecondPlayerName());
        prediction.setPlayer1WinProbability(Math.round(p1WinProb * 1000.0) / 1000.0);
        prediction.setPlayer2WinProbability(Math.round(p2WinProb * 1000.0) / 1000.0);
        prediction.setPredictedWinner(p1WinProb >= 0.5 ? p1Key : p2Key);
        prediction.setConfidence(Math.round(confidence * 1000.0) / 1000.0);
        prediction.setFactorScores(factorScores);

        return prediction;
    }

    // ============ FACTOR CALCULATIONS ============

    /**
     * Calculate ranking-based score.
     * Returns positive if player1 is ranked higher (lower number).
     * 
     * ⚠️ LIMITATION: This uses CURRENT ATP rankings, not historical rankings.
     * For accurate backtesting, we would need to store weekly ranking snapshots.
     * However, ATP rankings change slowly (weekly), so the impact is minimal for short backtests.
     * The OWL rating factor (which IS point-in-time) captures dynamic player strength more accurately.
     */
    private double calculateRankingScore(PlayerDocument player1, PlayerDocument player2) {
        if (player1 == null || player2 == null) return 0.0;
        
        Integer rank1 = player1.getCurrentRank();
        Integer rank2 = player2.getCurrentRank();
        
        if (rank1 == null && rank2 == null) return 0.0;
        if (rank1 == null) return -0.5; // Unranked vs ranked
        if (rank2 == null) return 0.5;  // Ranked vs unranked

        // Log scale for ranking difference
        double logRank1 = Math.log10(rank1 + 1);
        double logRank2 = Math.log10(rank2 + 1);
        double diff = logRank2 - logRank1; // Positive if p1 is better (lower rank)
        
        // Normalize to roughly -1 to 1 range
        return Math.tanh(diff);
    }

    /**
     * Calculate head-to-head score.
     * Returns positive if player1 has better H2H record.
     */
    private double calculateH2HScore(String p1Key, String p2Key, LocalDate asOfDate) {
        Optional<H2HDocument> h2hOpt = h2hRepository.findH2H(p1Key, p2Key);
        if (h2hOpt.isEmpty()) {
            // Try to calculate from fixtures
            return calculateH2HFromFixtures(p1Key, p2Key, asOfDate);
        }

        H2HDocument h2h = h2hOpt.get();
        List<H2HDocument.H2HMatch> matches = h2h.getMatches();
        if (matches == null || matches.isEmpty()) return 0.0;

        // Filter to matches before asOfDate
        List<H2HDocument.H2HMatch> relevantMatches = matches.stream()
                .filter(m -> m.getEventDate() != null && m.getEventDate().isBefore(asOfDate))
                .collect(Collectors.toList());

        if (relevantMatches.isEmpty()) return 0.0;

        // Count wins
        long p1Wins = relevantMatches.stream()
                .filter(m -> p1Key.equals(m.getWinnerKey()))
                .count();
        long p2Wins = relevantMatches.stream()
                .filter(m -> p2Key.equals(m.getWinnerKey()))
                .count();
        long total = p1Wins + p2Wins;

        if (total == 0) return 0.0;

        // Score based on win percentage difference
        double p1WinRate = (double) p1Wins / total;
        double score = (p1WinRate - 0.5) * 2.0; // Range: -1 to 1

        // Weight by number of matches (more matches = more reliable)
        double reliability = Math.min(1.0, total / 5.0);
        return score * reliability;
    }

    private double calculateH2HFromFixtures(String p1Key, String p2Key, LocalDate asOfDate) {
        List<FixtureDocument> h2hMatches = fixtureRepository.findH2HMatches(p1Key, p2Key);
        
        List<FixtureDocument> relevantMatches = h2hMatches.stream()
                .filter(f -> f.getEventDate() != null && f.getEventDate().isBefore(asOfDate))
                .collect(Collectors.toList());

        if (relevantMatches.isEmpty()) return 0.0;

        long p1Wins = relevantMatches.stream().filter(f -> f.isWinner(p1Key)).count();
        long p2Wins = relevantMatches.stream().filter(f -> f.isWinner(p2Key)).count();
        long total = p1Wins + p2Wins;

        if (total == 0) return 0.0;

        double p1WinRate = (double) p1Wins / total;
        double score = (p1WinRate - 0.5) * 2.0;
        double reliability = Math.min(1.0, total / 5.0);
        return score * reliability;
    }

    /**
     * Calculate recent form score based on last N matches.
     */
    private double calculateRecentFormScore(String p1Key, String p2Key, LocalDate asOfDate) {
        double p1Form = calculatePlayerForm(p1Key, asOfDate, 10);
        double p2Form = calculatePlayerForm(p2Key, asOfDate, 10);
        
        // Return difference (positive = p1 better form)
        return p1Form - p2Form;
    }

    private double calculatePlayerForm(String playerKey, LocalDate asOfDate, int matchCount) {
        List<FixtureDocument> recentMatches = fixtureRepository.findRecentMatchesByPlayer(playerKey, asOfDate);
        
        if (recentMatches.isEmpty()) return 0.0;

        // Sort by date descending and limit
        recentMatches = recentMatches.stream()
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .limit(matchCount)
                .collect(Collectors.toList());

        // Calculate weighted win rate (more recent matches weighted higher)
        double weightedWins = 0.0;
        double totalWeight = 0.0;
        
        for (int i = 0; i < recentMatches.size(); i++) {
            FixtureDocument match = recentMatches.get(i);
            double weight = 1.0 / (i + 1); // Exponential decay
            totalWeight += weight;
            
            if (match.isWinner(playerKey)) {
                weightedWins += weight;
            }
        }

        return totalWeight > 0 ? (weightedWins / totalWeight) - 0.5 : 0.0; // Center around 0
    }

    /**
     * Calculate surface-specific performance score.
     */
    private double calculateSurfaceScore(PlayerDocument player1, PlayerDocument player2, String surface) {
        if (surface == null || surface.isBlank()) return 0.0;

        double p1SurfaceWinRate = extractSurfaceWinRate(player1, surface);
        double p2SurfaceWinRate = extractSurfaceWinRate(player2, surface);

        // Return difference (positive = p1 better on surface)
        return (p1SurfaceWinRate - p2SurfaceWinRate) * 2.0; // Scale up
    }

    private double extractSurfaceWinRate(PlayerDocument player, String surface) {
        if (player == null || player.getRaw() == null) return 0.5;

        Object statsObj = player.getRaw().get("stats");
        if (!(statsObj instanceof List)) return 0.5;

        @SuppressWarnings("unchecked")
        List<org.bson.Document> statsList = (List<org.bson.Document>) statsObj;

        // Find most recent singles season
        String latestSeason = null;
        for (org.bson.Document stat : statsList) {
            String type = stat.getString("type");
            String season = stat.getString("season");
            if ("singles".equalsIgnoreCase(type) && season != null) {
                if (latestSeason == null || season.compareTo(latestSeason) > 0) {
                    latestSeason = season;
                }
            }
        }

        if (latestSeason == null) return 0.5;

        // Get surface stats from latest season
        for (org.bson.Document stat : statsList) {
            String type = stat.getString("type");
            String season = stat.getString("season");
            if ("singles".equalsIgnoreCase(type) && latestSeason.equals(season)) {
                String surfaceLower = surface.toLowerCase();
                String wonKey = surfaceLower + "_won";
                String lostKey = surfaceLower + "_lost";

                int won = parseIntSafe(stat.getString(wonKey));
                int lost = parseIntSafe(stat.getString(lostKey));
                int total = won + lost;

                return total > 0 ? (double) won / total : 0.5;
            }
        }

        return 0.5;
    }

    /**
     * Calculate fatigue score based on recent match load.
     */
    private double calculateFatigueScore(String p1Key, String p2Key, LocalDate asOfDate) {
        int p1MatchCount = countRecentMatches(p1Key, asOfDate, 14);
        int p2MatchCount = countRecentMatches(p2Key, asOfDate, 14);

        // More matches = more fatigue = disadvantage
        // Return positive if p2 is more fatigued (advantage to p1)
        double diff = p2MatchCount - p1MatchCount;
        return Math.tanh(diff / 3.0); // Normalize
    }

    private int countRecentMatches(String playerKey, LocalDate asOfDate, int days) {
        LocalDate startDate = asOfDate.minusDays(days);
        List<FixtureDocument> matches = fixtureRepository.findRecentMatchesByPlayer(playerKey, asOfDate);
        
        return (int) matches.stream()
                .filter(m -> m.getEventDate() != null && m.getEventDate().isAfter(startDate))
                .count();
    }

    /**
     * Calculate momentum score based on current streak.
     */
    private double calculateMomentumScore(String p1Key, String p2Key, LocalDate asOfDate) {
        int p1Streak = calculateWinStreak(p1Key, asOfDate);
        int p2Streak = calculateWinStreak(p2Key, asOfDate);

        // Positive streak difference = p1 has better momentum
        double diff = p1Streak - p2Streak;
        return Math.tanh(diff / 3.0); // Normalize
    }

    private int calculateWinStreak(String playerKey, LocalDate asOfDate) {
        List<FixtureDocument> recentMatches = fixtureRepository.findRecentMatchesByPlayer(playerKey, asOfDate);
        
        // Sort by date descending
        recentMatches = recentMatches.stream()
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .limit(10)
                .collect(Collectors.toList());

        int streak = 0;
        for (FixtureDocument match : recentMatches) {
            if (match.isWinner(playerKey)) {
                streak++;
            } else {
                break; // Streak broken
            }
        }
        return streak;
    }

    // ============ ODDS-ADJUSTED PERFORMANCE SCORE (OAPS) ============

    /**
     * Calculate OAPS factor - measures how well players perform vs market expectations.
     * A player consistently beating the odds (winning as underdog, dominating as favorite)
     * has positive OAPS, indicating hidden momentum.
     */
    private double calculateOAPSScore(String p1Key, String p2Key, LocalDate asOfDate) {
        double p1Oaps = calculatePlayerOAPS(p1Key, asOfDate, 8);
        double p2Oaps = calculatePlayerOAPS(p2Key, asOfDate, 8);
        
        // Return difference (positive = p1 has been outperforming expectations more)
        return p1Oaps - p2Oaps;
    }

    /**
     * Calculate a player's rolling OAPS over their recent matches.
     * OAPS = sum of (actual_result - expected_result) * dominance_multiplier
     * 
     * @param playerKey The player to analyze
     * @param asOfDate Point-in-time date filter
     * @param matchCount Number of recent matches to consider
     * @return Rolling OAPS score (positive = outperforming, negative = underperforming)
     */
    private double calculatePlayerOAPS(String playerKey, LocalDate asOfDate, int matchCount) {
        List<FixtureDocument> recentMatches = fixtureRepository.findRecentMatchesByPlayer(playerKey, asOfDate);
        
        if (recentMatches.isEmpty()) return 0.0;

        // Sort by date descending and limit
        recentMatches = recentMatches.stream()
                .filter(m -> m.getEventDate() != null && m.isFinished())
                .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                .limit(matchCount)
                .collect(Collectors.toList());

        if (recentMatches.isEmpty()) return 0.0;

        // Pre-fetch odds for efficiency
        List<String> matchKeys = recentMatches.stream()
                .map(FixtureDocument::getEventKey)
                .collect(Collectors.toList());
        Map<String, OddsDocument> oddsMap = oddsRepository.findByMatchKeyIn(matchKeys).stream()
                .collect(Collectors.toMap(OddsDocument::getMatchKey, o -> o, (a, b) -> a));

        double totalOaps = 0.0;
        double totalWeight = 0.0;
        int matchesWithOdds = 0;

        for (int i = 0; i < recentMatches.size(); i++) {
            FixtureDocument match = recentMatches.get(i);
            OddsDocument odds = oddsMap.get(match.getEventKey());
            
            if (odds == null) continue; // Skip matches without odds data
            
            double oaps = calculateSingleMatchOAPS(match, playerKey, odds);
            if (Double.isNaN(oaps)) continue;
            
            // Exponential decay - more recent matches weighted higher
            double weight = 1.0 / (i + 1);
            totalOaps += oaps * weight;
            totalWeight += weight;
            matchesWithOdds++;
        }

        if (matchesWithOdds == 0 || totalWeight == 0) return 0.0;

        // Scale by reliability (more matches with odds = more reliable)
        double reliability = Math.min(1.0, matchesWithOdds / 4.0);
        return (totalOaps / totalWeight) * reliability;
    }

    /**
     * Calculate OAPS for a single match.
     * 
     * Formula: (actualResult - impliedProbability) * dominanceMultiplier
     * 
     * Examples:
     * - Underdog (3.0 odds, 33% implied) wins straight sets: (1.0 - 0.33) * 1.5 = +1.0
     * - Favorite (1.3 odds, 77% implied) wins in 5 sets: (1.0 - 0.77) * 0.8 = +0.18
     * - Underdog (2.5 odds, 40% implied) loses in 5 sets: (0.0 - 0.40) * 1.5 = -0.6
     */
    private double calculateSingleMatchOAPS(FixtureDocument match, String playerKey, OddsDocument odds) {
        if (odds == null) return Double.NaN;
        
        // Determine if this player was player 1 (home) or player 2 (away)
        boolean isPlayer1 = playerKey.equals(match.getFirstPlayerKey());
        Double playerOdds = isPlayer1 ? odds.getHomeOdds() : odds.getAwayOdds();
        
        if (playerOdds == null || playerOdds <= 1.0) return Double.NaN;
        
        // Calculate implied win probability from odds
        // Adjust for bookmaker overround (assume ~5% margin)
        double rawProb = 1.0 / playerOdds;
        double impliedWinProb = Math.min(0.95, rawProb * 0.95); // Remove some margin
        
        // Determine actual result
        boolean won = match.isWinner(playerKey);
        double actualResult = won ? 1.0 : 0.0;
        
        // Calculate dominance multiplier from score
        double dominanceMultiplier = calculateDominanceMultiplier(match, playerKey, won);
        
        // OAPS = (actual - expected) * dominance
        return (actualResult - impliedWinProb) * dominanceMultiplier;
    }

    /**
     * Calculate dominance multiplier based on how convincing the win/loss was.
     * 
     * For wins:
     * - Straight sets (2-0 or 3-0) = dominant = 1.5x
     * - 3-1 in grand slam / 2-1 in ATP = solid = 1.2x
     * - Close 5-setter / 3-setter = struggled = 0.8x
     * 
     * For losses:
     * - Straight sets loss = collapsed = 0.7x (reduces negative impact less)
     * - Close loss = fought hard = 1.5x (better than expected)
     */
    private double calculateDominanceMultiplier(FixtureDocument match, String playerKey, boolean won) {
        String score = match.getScore();
        if (score == null || score.isBlank()) return 1.0;
        
        // Parse set scores from score string like "6-3, 7-6, 6-4" or "2-1" (sets won-lost)
        int[] setCount = parseSetCount(score, match, playerKey);
        int setsWon = setCount[0];
        int setsLost = setCount[1];
        int totalSets = setsWon + setsLost;
        
        if (totalSets == 0) return 1.0;
        
        if (won) {
            // Winning scenarios - higher multiplier for dominant wins
            if (totalSets == 2 && setsLost == 0) return 1.5;      // 2-0 straight sets (best of 3)
            if (totalSets == 3 && setsLost == 0) return 1.5;      // 3-0 straight sets (grand slam)
            if (totalSets == 3 && setsLost == 1) return 1.2;      // 2-1 (solid)
            if (totalSets == 4 && setsLost == 1) return 1.2;      // 3-1 (solid)
            if (totalSets == 5) return 0.8;                        // 3-2 (struggled)
            return 1.0; // Default
        } else {
            // Losing scenarios - higher multiplier means "fought harder than expected"
            // which is actually GOOD for the player (reduces negative OAPS impact)
            if (totalSets == 2 && setsWon == 0) return 0.7;       // 0-2 straight sets loss (bad)
            if (totalSets == 3 && setsWon == 0) return 0.7;       // 0-3 straight sets loss (bad)
            if (totalSets == 3 && setsWon == 1) return 1.0;       // 1-2 (competitive)
            if (totalSets == 4 && setsWon == 1) return 1.2;       // 1-3 (fought back)
            if (totalSets == 5) return 1.5;                        // 2-3 (pushed to limit - impressive loss)
            return 1.0; // Default
        }
    }

    /**
     * Parse the set count from a score string.
     * Returns [setsWon, setsLost] for the given player.
     */
    private int[] parseSetCount(String score, FixtureDocument match, String playerKey) {
        // Handle simple "X-Y" format (sets summary)
        if (score.matches("^\\d+-\\d+$")) {
            String[] parts = score.split("-");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            boolean isPlayer1 = playerKey.equals(match.getFirstPlayerKey());
            return isPlayer1 ? new int[]{first, second} : new int[]{second, first};
        }
        
        // Handle detailed set scores like "6-3, 7-6, 6-4" or "6-3 7-6 6-4"
        String[] sets = score.split("[,\\s]+");
        int setsWon = 0;
        int setsLost = 0;
        boolean isPlayer1 = playerKey.equals(match.getFirstPlayerKey());
        
        for (String set : sets) {
            set = set.trim();
            // Match patterns like "6-3", "7-6(5)", "6-7(3)"
            if (set.matches("^\\d+-\\d+(\\(\\d+\\))?$")) {
                String cleanSet = set.replaceAll("\\(\\d+\\)", ""); // Remove tiebreak score
                String[] games = cleanSet.split("-");
                if (games.length == 2) {
                    try {
                        int g1 = Integer.parseInt(games[0]);
                        int g2 = Integer.parseInt(games[1]);
                        
                        // Player 1 wins set if g1 > g2
                        if (g1 > g2) {
                            if (isPlayer1) setsWon++; else setsLost++;
                        } else if (g2 > g1) {
                            if (isPlayer1) setsLost++; else setsWon++;
                        }
                        // Tiebreak: if equal games (shouldn't happen with tiebreak removed)
                    } catch (NumberFormatException e) {
                        // Skip unparseable set
                    }
                }
            }
        }
        
        return new int[]{setsWon, setsLost};
    }

    // ============ OWL RATING FACTORS (POINT-IN-TIME) ============

    /**
     * Calculate OWL rating-based score using Elo probability formula.
     * Uses POINT-IN-TIME rating as of asOfDate for accurate backtesting.
     * Returns positive if player1 has higher OWL rating.
     */
    private double calculateOwlRatingScore(String p1Key, String p2Key, LocalDate asOfDate) {
        // Use point-in-time ratings from OwlRatingService
        double rating1 = owlRatingService.getRatingAsOfDate(p1Key, asOfDate);
        double rating2 = owlRatingService.getRatingAsOfDate(p2Key, asOfDate);

        // Check if either player has meaningful OWL history before this date
        boolean hasP1Data = rating1 != 1500.0; // Non-default rating means they have history
        boolean hasP2Data = rating2 != 1500.0;

        if (!hasP1Data && !hasP2Data) {
            return 0.0; // No OWL data for either player before this date
        }

        // Elo win probability formula
        double expectedP1Win = 1.0 / (1.0 + Math.pow(10, (rating2 - rating1) / 400.0));

        // Convert to -1 to +1 score (positive favors p1)
        double score = (expectedP1Win - 0.5) * 2.0;

        // Scale by reliability (both players need OWL data for full weight)
        double reliability = 0.5;
        if (hasP1Data) reliability += 0.25;
        if (hasP2Data) reliability += 0.25;

        return score * reliability;
    }

    /**
     * Calculate OWL momentum-based score.
     * Uses POINT-IN-TIME momentum as of asOfDate for accurate backtesting.
     * Returns positive if player1 has better momentum.
     */
    private double calculateOwlMomentumScore(String p1Key, String p2Key, LocalDate asOfDate) {
        // Use point-in-time momentum from OwlRatingService
        double momentum1 = owlRatingService.getMomentumAsOfDate(p1Key, asOfDate);
        double momentum2 = owlRatingService.getMomentumAsOfDate(p2Key, asOfDate);

        // Momentum difference - normalize to roughly -1 to +1 range
        // Typical momentum range is -100 to +100
        double momentumDiff = momentum1 - momentum2;
        double score = Math.tanh(momentumDiff / 50.0); // tanh smoothing

        // Get point-in-time trend (based on momentum direction)
        String trend1 = determineTrend(momentum1);
        String trend2 = determineTrend(momentum2);

        // Hot players get extra boost, cold players get penalty
        double trendBonus = 0.0;
        trendBonus += getTrendBonus(trend1);  // Positive for p1
        trendBonus -= getTrendBonus(trend2);  // Negative if p2 is hot

        // Combine momentum score with trend bonus
        score = (score * 0.7) + (trendBonus * 0.3);

        // Scale by reliability - check if they have meaningful momentum data
        double reliability = 0.5;
        if (momentum1 != 0.0) reliability += 0.25;
        if (momentum2 != 0.0) reliability += 0.25;

        return Math.max(-1.0, Math.min(1.0, score * reliability));
    }

    /**
     * Determine trend label from momentum score.
     */
    private String determineTrend(double momentum) {
        if (momentum > 30) return "hot";
        if (momentum > 10) return "rising";
        if (momentum < -30) return "cold";
        if (momentum < -10) return "falling";
        return "stable";
    }

    /**
     * Get trend bonus/penalty value.
     */
    private double getTrendBonus(String trend) {
        if (trend == null) return 0.0;
        return switch (trend) {
            case "hot" -> 0.4;      // Big boost
            case "rising" -> 0.2;   // Small boost
            case "stable" -> 0.0;
            case "cooling" -> -0.2; // Small penalty
            case "cold" -> -0.4;    // Big penalty
            default -> 0.0;
        };
    }

    /**
     * Adjust prediction confidence based on player consistency.
     * Volatile players make predictions less reliable.
     * Uses POINT-IN-TIME consistency as of asOfDate for accurate backtesting.
     */
    private double adjustConfidenceForOwlConsistency(double confidence, String p1Key, String p2Key, LocalDate asOfDate) {
        // Use point-in-time consistency from OwlRatingService
        double consistency1 = owlRatingService.getConsistencyAsOfDate(p1Key, asOfDate, 10);
        double consistency2 = owlRatingService.getConsistencyAsOfDate(p2Key, asOfDate, 10);

        // Default to "steady" if no consistency data available
        if (consistency1 == 0.0) consistency1 = 15.0;
        if (consistency2 == 0.0) consistency2 = 15.0;

        // Average consistency (higher = more volatile)
        double avgConsistency = (consistency1 + consistency2) / 2.0;

        // Calculate confidence multiplier
        // Rock Solid (σ < 8): 1.1x confidence
        // Steady (σ 8-15): 1.0x confidence
        // Volatile (σ 15-25): 0.9x confidence
        // Wild Card (σ > 25): 0.8x confidence
        double multiplier;
        if (avgConsistency < 8) {
            multiplier = 1.1;
        } else if (avgConsistency < 15) {
            multiplier = 1.0;
        } else if (avgConsistency < 25) {
            multiplier = 0.9;
        } else {
            multiplier = 0.8;
        }

        // Apply multiplier and clamp
        return Math.max(0.1, Math.min(0.95, confidence * multiplier));
    }

    /**
     * Calculate confidence based on factor agreement.
     */
    private double calculateConfidence(Map<String, Double> factorScores, Map<String, Double> weights) {
        // Count how many factors agree on the same direction
        int positive = 0;
        int negative = 0;
        double totalWeight = 0.0;

        for (Map.Entry<String, Double> entry : factorScores.entrySet()) {
            double weight = weights.getOrDefault(entry.getKey(), 0.0);
            if (weight <= 0) continue;
            
            totalWeight += weight;
            if (entry.getValue() > 0.1) positive++;
            else if (entry.getValue() < -0.1) negative++;
        }

        // Higher agreement = higher confidence
        int agreementCount = Math.max(positive, negative);
        int totalFactors = positive + negative;
        
        if (totalFactors == 0) return 0.3; // Low confidence if no clear signals

        double agreement = (double) agreementCount / totalFactors;
        
        // Scale confidence: 0.3 (low agreement) to 0.9 (full agreement)
        return 0.3 + (agreement * 0.6);
    }

    // ============ HELPER METHODS ============

    private Map<String, Double> getModelWeights(String modelId) {
        return modelConfigRepository.findByModelId(modelId)
                .map(ModelConfigDocument::getWeights)
                .filter(w -> w != null && !w.isEmpty())
                .orElse(DEFAULT_WEIGHTS);
    }

    private int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Get all upcoming matches that can be predicted.
     * Uses the more inclusive query that finds matches without a winner (not yet finished).
     */
    public List<FixtureDocument> getUpcomingMatches(int days) {
        LocalDate today = LocalDate.now();
        // Use the more inclusive query that checks for null winner
        List<FixtureDocument> matches = fixtureRepository.findUnfinishedByDateRange(today, today.plusDays(days));
        log.debug("Found {} upcoming/unfinished matches in next {} days", matches.size(), days);
        return matches;
    }

    /**
     * Get unfinished matches (matches without results) within a specific date range.
     * Useful for predicting reset/historical matches.
     */
    public List<FixtureDocument> getUnfinishedMatches(LocalDate startDate, LocalDate endDate) {
        List<FixtureDocument> matches = fixtureRepository.findUnfinishedByDateRange(startDate, endDate);
        log.debug("Found {} unfinished matches between {} and {}", matches.size(), startDate, endDate);
        return matches;
    }

    /**
     * Batch predict all upcoming matches.
     */
    public List<PredictionDocument> predictUpcoming(String modelId, int days) {
        List<FixtureDocument> upcoming = getUpcomingMatches(days);
        List<PredictionDocument> predictions = new ArrayList<>();

        for (FixtureDocument fixture : upcoming) {
            try {
                predictions.add(predict(fixture.getEventKey(), modelId));
            } catch (Exception e) {
                log.warn("Failed to predict match {}: {}", fixture.getEventKey(), e.getMessage());
            }
        }

        return predictions;
    }
}

