package com.tennis.prediction.service;

import com.tennis.prediction.model.OwlRatingDocument;
import com.tennis.prediction.model.RatingChange;
import com.tennis.prediction.model.readonly.FixtureDocument;
import com.tennis.prediction.model.readonly.OddsDocument;
import com.tennis.prediction.model.readonly.PlayerDocument;
import com.tennis.prediction.model.readonly.TournamentDocument;
import com.tennis.prediction.repository.OwlRatingRepository;
import com.tennis.prediction.repository.readonly.FixtureReadRepository;
import com.tennis.prediction.repository.readonly.OddsReadRepository;
import com.tennis.prediction.repository.readonly.PlayerReadRepository;
import com.tennis.prediction.repository.readonly.TournamentReadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OWL (Odds-Weighted Ladder) Rating Service.
 * 
 * Calculates and maintains a dynamic player rating system that uses
 * betting odds to determine expected outcomes and rewards upsets accordingly.
 * 
 * Key principles:
 * - Favorites who win as expected gain minimal points
 * - Underdogs who pull off upsets gain significant points
 * - Score margin (dominance) affects point exchange
 * - Tournament importance affects point values
 */
@Service
public class OwlRatingService {

    private static final Logger log = LoggerFactory.getLogger(OwlRatingService.class);

    // Base point constant for calculations
    private static final double BASE_K = 32.0;
    
    // Starting rating for new players
    private static final double STARTING_RATING = 1500.0;
    
    // Grand Slam tournament keys (partial matches)
    private static final Set<String> GRAND_SLAM_KEYWORDS = Set.of(
            "australian-open", "roland-garros", "french-open", "wimbledon", "us-open"
    );
    
    // Masters 1000 keywords
    private static final Set<String> MASTERS_KEYWORDS = Set.of(
            "indian-wells", "miami", "monte-carlo", "madrid", "rome", 
            "canada", "cincinnati", "shanghai", "paris"
    );

    private final OwlRatingRepository owlRatingRepository;
    private final FixtureReadRepository fixtureRepository;
    private final OddsReadRepository oddsRepository;
    private final PlayerReadRepository playerRepository;
    private final TournamentReadRepository tournamentRepository;

    public OwlRatingService(
            OwlRatingRepository owlRatingRepository,
            FixtureReadRepository fixtureRepository,
            OddsReadRepository oddsRepository,
            PlayerReadRepository playerRepository,
            TournamentReadRepository tournamentRepository
    ) {
        this.owlRatingRepository = owlRatingRepository;
        this.fixtureRepository = fixtureRepository;
        this.oddsRepository = oddsRepository;
        this.playerRepository = playerRepository;
        this.tournamentRepository = tournamentRepository;
    }

    // ============ PUBLIC API ============

    /**
     * Get leaderboard of top rated players.
     */
    public List<OwlRatingDocument> getLeaderboard(int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMinMatchesPlayedOrderByRatingDesc(
                minMatches, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "rating")));
        
        // Assign ranks
        for (int i = 0; i < all.size(); i++) {
            all.get(i).setOwlRank(i + 1);
        }
        
        return all;
    }

    /**
     * Get a player's rating, creating if doesn't exist.
     */
    public OwlRatingDocument getOrCreateRating(String playerKey, String playerName) {
        return owlRatingRepository.findByPlayerKey(playerKey)
                .orElseGet(() -> {
                    OwlRatingDocument rating = new OwlRatingDocument(playerKey, playerName);
                    // Look up ATP rank from player data
                    playerRepository.findByPlayerKey(playerKey).ifPresent(player -> {
                        rating.setAtpRank(player.getCurrentRank());
                    });
                    return owlRatingRepository.save(rating);
                });
    }

    /**
     * Update ATP ranks for all rated players from player data.
     * Processes in batches to avoid memory issues.
     */
    public int updateAllAtpRanks() {
        List<OwlRatingDocument> allRatings = owlRatingRepository.findAll();
        
        // Collect all player keys
        List<String> playerKeys = allRatings.stream()
                .map(OwlRatingDocument::getPlayerKey)
                .collect(Collectors.toList());
        
        // Batch lookup players by their keys (more efficient)
        Map<String, Integer> playerRanks = new HashMap<>();
        
        // Process in batches of 100 to avoid memory issues
        int batchSize = 100;
        for (int i = 0; i < playerKeys.size(); i += batchSize) {
            List<String> batch = playerKeys.subList(i, Math.min(i + batchSize, playerKeys.size()));
            List<PlayerDocument> players = playerRepository.findByPlayerKeyIn(batch);
            for (PlayerDocument player : players) {
                if (player.getCurrentRank() != null) {
                    playerRanks.put(player.getPlayerKey(), player.getCurrentRank());
                }
            }
        }
        
        log.info("Found ATP ranks for {} players", playerRanks.size());
        
        // Update ratings
        int updated = 0;
        for (OwlRatingDocument rating : allRatings) {
            Integer rank = playerRanks.get(rating.getPlayerKey());
            if (rank != null) {
                rating.setAtpRank(rank);
                updated++;
            }
        }
        
        owlRatingRepository.saveAll(allRatings);
        log.info("Updated ATP ranks for {} players", updated);
        return updated;
    }

    /**
     * Get a player's rating with rank calculated.
     */
    public Optional<OwlRatingDocument> getPlayerRating(String playerKey) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingRepository.findByPlayerKey(playerKey);
        
        if (ratingOpt.isPresent()) {
            // Calculate current rank
            OwlRatingDocument rating = ratingOpt.get();
            long higherRated = owlRatingRepository.findByRatingBetween(rating.getRating() + 0.01, 10000)
                    .stream()
                    .filter(r -> r.getMatchesPlayed() >= 5)
                    .count();
            rating.setOwlRank((int) higherRated + 1);
        }
        
        return ratingOpt;
    }

    // ============ POINT-IN-TIME QUERIES (FOR BACKTESTING) ============

    /**
     * Get a player's OWL rating as of a specific date.
     * Uses the recentChanges history to calculate what the rating was BEFORE the given date.
     * This is essential for accurate backtesting without data leakage.
     * 
     * @param playerKey The player's key
     * @param asOfDate Calculate rating as of this date (matches ON this date are NOT included)
     * @return The rating value, or STARTING_RATING if no history before that date
     */
    public double getRatingAsOfDate(String playerKey, LocalDate asOfDate) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingRepository.findByPlayerKey(playerKey);
        
        if (ratingOpt.isEmpty()) {
            return STARTING_RATING;
        }
        
        OwlRatingDocument rating = ratingOpt.get();
        List<RatingChange> changes = rating.getRecentChanges();
        
        if (changes == null || changes.isEmpty()) {
            return STARTING_RATING;
        }
        
        // Find the last change BEFORE asOfDate
        // Changes are stored in chronological order (oldest first in the list after updateMomentumAndConsistency)
        // But we need to find the most recent one BEFORE asOfDate
        RatingChange lastChangeBefore = null;
        for (RatingChange change : changes) {
            if (change.getMatchDate() != null && change.getMatchDate().isBefore(asOfDate)) {
                lastChangeBefore = change;
            }
        }
        
        if (lastChangeBefore != null) {
            return lastChangeBefore.getRatingAfter();
        }
        
        return STARTING_RATING;
    }

    /**
     * Get a player's momentum score as of a specific date.
     * Calculates momentum using only matches BEFORE the given date.
     * 
     * @param playerKey The player's key
     * @param asOfDate Calculate momentum as of this date
     * @param windowSize Number of recent matches to consider for momentum (default 7)
     * @return The momentum score (sum of recent rating changes)
     */
    public double getMomentumAsOfDate(String playerKey, LocalDate asOfDate, int windowSize) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingRepository.findByPlayerKey(playerKey);
        
        if (ratingOpt.isEmpty()) {
            return 0.0;
        }
        
        OwlRatingDocument rating = ratingOpt.get();
        List<RatingChange> changes = rating.getRecentChanges();
        
        if (changes == null || changes.isEmpty()) {
            return 0.0;
        }
        
        // Filter to changes BEFORE asOfDate, sort by date descending, take last N
        List<RatingChange> relevantChanges = changes.stream()
                .filter(c -> c.getMatchDate() != null && c.getMatchDate().isBefore(asOfDate))
                .sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate())) // Most recent first
                .limit(windowSize)
                .collect(Collectors.toList());
        
        if (relevantChanges.isEmpty()) {
            return 0.0;
        }
        
        // Sum the point changes (momentum = sum of recent rating changes)
        return relevantChanges.stream()
                .mapToDouble(RatingChange::getPointsChange)
                .sum();
    }

    /**
     * Get momentum with default window size of 7 matches.
     */
    public double getMomentumAsOfDate(String playerKey, LocalDate asOfDate) {
        return getMomentumAsOfDate(playerKey, asOfDate, 7);
    }

    /**
     * Get a player's consistency score as of a specific date.
     * Lower standard deviation = more consistent player.
     * 
     * @param playerKey The player's key
     * @param asOfDate Calculate consistency as of this date
     * @param windowSize Number of recent matches to consider
     * @return The consistency score (std dev of rating changes, lower = more consistent)
     */
    public double getConsistencyAsOfDate(String playerKey, LocalDate asOfDate, int windowSize) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingRepository.findByPlayerKey(playerKey);
        
        if (ratingOpt.isEmpty()) {
            return 0.0;
        }
        
        OwlRatingDocument rating = ratingOpt.get();
        List<RatingChange> changes = rating.getRecentChanges();
        
        if (changes == null || changes.isEmpty()) {
            return 0.0;
        }
        
        // Filter to changes BEFORE asOfDate
        List<Double> pointChanges = changes.stream()
                .filter(c -> c.getMatchDate() != null && c.getMatchDate().isBefore(asOfDate))
                .sorted((a, b) -> b.getMatchDate().compareTo(a.getMatchDate()))
                .limit(windowSize)
                .map(RatingChange::getPointsChange)
                .collect(Collectors.toList());
        
        if (pointChanges.size() < 2) {
            return 0.0; // Not enough data for std dev
        }
        
        // Calculate standard deviation
        double mean = pointChanges.stream().mapToDouble(d -> d).average().orElse(0.0);
        double variance = pointChanges.stream()
                .mapToDouble(d -> Math.pow(d - mean, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }

    /**
     * Process a single match and update both players' ratings.
     */
    public PointExchange processMatch(FixtureDocument match, OddsDocument odds) {
        if (match.getWinner() == null) {
            log.debug("Match {} has no winner, skipping", match.getEventKey());
            return null;
        }

        String winnerKey = determineWinnerKey(match);
        String loserKey = winnerKey.equals(match.getFirstPlayerKey()) 
                ? match.getSecondPlayerKey() 
                : match.getFirstPlayerKey();
        
        String winnerName = winnerKey.equals(match.getFirstPlayerKey())
                ? match.getFirstPlayerName()
                : match.getSecondPlayerName();
        String loserName = loserKey.equals(match.getFirstPlayerKey())
                ? match.getFirstPlayerName()
                : match.getSecondPlayerName();

        // Get or create ratings
        OwlRatingDocument winnerRating = getOrCreateRating(winnerKey, winnerName);
        OwlRatingDocument loserRating = getOrCreateRating(loserKey, loserName);

        // Calculate point exchange
        PointExchange exchange = calculatePoints(match, odds, winnerRating, loserRating);

        // Get tournament name for history
        String tournamentName = getTournamentName(match.getTournamentKey());

        // Update winner
        double winnerBefore = winnerRating.getRating();
        winnerRating.updateRating(exchange.pointsGained(), true);
        
        RatingChange winnerChange = RatingChange.builder()
                .matchKey(match.getEventKey())
                .matchDate(match.getEventDate())
                .opponentKey(loserKey)
                .opponentName(loserName)
                .opponentRatingBefore(loserRating.getRating())
                .won(true)
                .score(match.getScore())
                .odds(exchange.winnerOdds())
                .expectedWinProb(exchange.expectedWinProb())
                .dominanceMultiplier(exchange.dominanceMultiplier())
                .tournamentMultiplier(exchange.tournamentMultiplier())
                .pointsChange(exchange.pointsGained())
                .ratingBefore(winnerBefore)
                .ratingAfter(winnerRating.getRating())
                .tournamentName(tournamentName)
                .build();
        winnerRating.addRatingChange(winnerChange);

        // Update loser
        double loserBefore = loserRating.getRating();
        loserRating.updateRating(-exchange.pointsLost(), false);
        
        RatingChange loserChange = RatingChange.builder()
                .matchKey(match.getEventKey())
                .matchDate(match.getEventDate())
                .opponentKey(winnerKey)
                .opponentName(winnerName)
                .opponentRatingBefore(winnerRating.getRating())
                .won(false)
                .score(match.getScore())
                .odds(exchange.loserOdds())
                .expectedWinProb(1 - exchange.expectedWinProb())
                .dominanceMultiplier(exchange.loserDominanceMultiplier())
                .tournamentMultiplier(exchange.tournamentMultiplier())
                .pointsChange(-exchange.pointsLost())
                .ratingBefore(loserBefore)
                .ratingAfter(loserRating.getRating())
                .tournamentName(tournamentName)
                .build();
        loserRating.addRatingChange(loserChange);

        // Save both
        owlRatingRepository.save(winnerRating);
        owlRatingRepository.save(loserRating);

        log.debug("Match {}: {} ({}) beat {} ({}). Points: +{:.1f} / -{:.1f}",
                match.getEventKey(), winnerName, String.format("%.1f", winnerRating.getRating()),
                loserName, String.format("%.1f", loserRating.getRating()),
                exchange.pointsGained(), exchange.pointsLost());

        return exchange;
    }

    /**
     * Initialize ratings from historical matches.
     * Processes all finished matches in chronological order.
     * Uses batched processing to avoid OutOfMemoryError.
     */
    public InitializationResult initializeFromHistory(LocalDate startDate, LocalDate endDate) {
        log.info("Initializing OWL ratings from {} to {}", startDate, endDate);

        // Clear existing ratings
        owlRatingRepository.deleteAll();
        log.info("Cleared existing ratings");

        int processed = 0;
        int skipped = 0;
        int withOdds = 0;

        // Process day by day to avoid loading too much data at once
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            try {
                // Get matches for this day only
                List<FixtureDocument> dayMatches = fixtureRepository.findFinishedByDateRange(currentDate, currentDate);
                
                if (!dayMatches.isEmpty()) {
                    // Sort by event key for deterministic ordering
                    dayMatches.sort(Comparator.comparing(FixtureDocument::getEventKey));
                    
                    // Fetch odds for this day's matches
                    List<String> dayMatchKeys = dayMatches.stream()
                            .map(FixtureDocument::getEventKey)
                            .collect(Collectors.toList());
                    Map<String, OddsDocument> dayOddsMap = oddsRepository.findByMatchKeyIn(dayMatchKeys).stream()
                            .collect(Collectors.toMap(OddsDocument::getMatchKey, o -> o, (a, b) -> a));

                    for (FixtureDocument match : dayMatches) {
                        try {
                            if (match.getWinner() == null) {
                                skipped++;
                                continue;
                            }

                            OddsDocument odds = dayOddsMap.get(match.getEventKey());
                            if (odds != null) withOdds++;

                            processMatch(match, odds);
                            processed++;

                        } catch (Exception e) {
                            log.warn("Error processing match {}: {}", match.getEventKey(), e.getMessage());
                            skipped++;
                        }
                    }
                    
                    // Clear day references to help GC
                    dayMatches.clear();
                    dayOddsMap.clear();
                }
                
                if (processed % 200 == 0 && processed > 0) {
                    log.info("Processed {} matches... (current date: {})", processed, currentDate);
                }
                
            } catch (Exception e) {
                log.warn("Error processing date {}: {}", currentDate, e.getMessage());
            }
            
            currentDate = currentDate.plusDays(1);
        }

        // Update all ranks
        updateAllRanks();
        
        // Update ATP ranks from player data
        int atpRanksUpdated = updateAllAtpRanks();

        log.info("Initialization complete. Processed: {}, Skipped: {}, With Odds: {}, ATP Ranks Updated: {}",
                processed, skipped, withOdds, atpRanksUpdated);

        return new InitializationResult(processed, skipped, withOdds, 
                owlRatingRepository.count(), startDate, endDate);
    }

    /**
     * Update ranks for all players based on current rating.
     */
    public void updateAllRanks() {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(0);
        all.sort((a, b) -> Double.compare(b.getRating(), a.getRating()));
        
        for (int i = 0; i < all.size(); i++) {
            all.get(i).setOwlRank(i + 1);
        }
        
        owlRatingRepository.saveAll(all);
        log.info("Updated ranks for {} players", all.size());
    }

    /**
     * Search players by name.
     */
    public List<OwlRatingDocument> searchPlayers(String query) {
        return owlRatingRepository.searchByName(query);
    }

    // ============ MOMENTUM & TRENDS API ============

    /**
     * Get hottest players (highest momentum score).
     */
    public List<OwlRatingDocument> getHottestPlayers(int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        // Sort by momentum score descending
        all.sort((a, b) -> Double.compare(b.getMomentumScore(), a.getMomentumScore()));
        
        // Update OWL ranks and return top N
        return all.stream()
                .limit(limit)
                .toList();
    }

    /**
     * Get coldest players (lowest momentum score).
     */
    public List<OwlRatingDocument> getColdestPlayers(int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        // Sort by momentum score ascending (most negative first)
        all.sort(Comparator.comparingDouble(OwlRatingDocument::getMomentumScore));
        
        return all.stream()
                .limit(limit)
                .toList();
    }

    /**
     * Get most consistent players (lowest consistency score / std dev).
     */
    public List<OwlRatingDocument> getMostConsistentPlayers(int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        // Sort by consistency score ascending (lower = more consistent)
        // Exclude players with 0 consistency (not enough data)
        all.sort(Comparator.comparingDouble(OwlRatingDocument::getConsistencyScore));
        
        return all.stream()
                .filter(r -> r.getConsistencyScore() > 0) // Has enough matches for std dev
                .limit(limit)
                .toList();
    }

    /**
     * Get most volatile/wild card players (highest consistency score / std dev).
     */
    public List<OwlRatingDocument> getMostVolatilePlayers(int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        // Sort by consistency score descending (higher = more volatile)
        all.sort((a, b) -> Double.compare(b.getConsistencyScore(), a.getConsistencyScore()));
        
        return all.stream()
                .filter(r -> r.getConsistencyScore() > 0)
                .limit(limit)
                .toList();
    }

    /**
     * Get rising stars - players with low ATP rank but high momentum.
     * These are potential breakout candidates.
     */
    public List<OwlRatingDocument> getRisingStars(int limit, int minAtpRank, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        return all.stream()
                // Filter to players ranked 100+ or unranked
                .filter(r -> r.getAtpRank() == null || r.getAtpRank() >= minAtpRank)
                // Filter to players with positive momentum
                .filter(r -> r.getMomentumScore() > 10)
                // Sort by momentum descending
                .sorted((a, b) -> Double.compare(b.getMomentumScore(), a.getMomentumScore()))
                .limit(limit)
                .toList();
    }

    /**
     * Get players by momentum trend.
     */
    public List<OwlRatingDocument> getPlayersByTrend(String trend, int limit, int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        return all.stream()
                .filter(r -> trend.equalsIgnoreCase(r.getMomentumTrend()))
                .sorted((a, b) -> Double.compare(b.getRating(), a.getRating()))
                .limit(limit)
                .toList();
    }

    /**
     * Get comprehensive trend statistics.
     */
    public TrendStats getTrendStats(int minMatches) {
        List<OwlRatingDocument> all = owlRatingRepository.findByMatchesPlayedGreaterThan(minMatches);
        
        int hot = 0, rising = 0, stable = 0, cooling = 0, cold = 0;
        
        for (OwlRatingDocument player : all) {
            String trend = player.getMomentumTrend();
            if (trend == null) trend = "stable";
            switch (trend) {
                case "hot" -> hot++;
                case "rising" -> rising++;
                case "stable" -> stable++;
                case "cooling" -> cooling++;
                case "cold" -> cold++;
            }
        }
        
        double avgMomentum = all.stream()
                .mapToDouble(OwlRatingDocument::getMomentumScore)
                .average()
                .orElse(0);
        
        double avgConsistency = all.stream()
                .filter(r -> r.getConsistencyScore() > 0)
                .mapToDouble(OwlRatingDocument::getConsistencyScore)
                .average()
                .orElse(0);
        
        return new TrendStats(hot, rising, stable, cooling, cold, 
                all.size(), avgMomentum, avgConsistency);
    }

    /**
     * Get player rating history for charting.
     * Returns a list of (date, rating) pairs for Chart.js.
     */
    public List<RatingHistoryPoint> getPlayerRatingHistory(String playerKey) {
        Optional<OwlRatingDocument> ratingOpt = owlRatingRepository.findByPlayerKey(playerKey);
        if (ratingOpt.isEmpty()) {
            return List.of();
        }
        
        OwlRatingDocument rating = ratingOpt.get();
        List<RatingChange> changes = rating.getRecentChanges();
        
        if (changes.isEmpty()) {
            return List.of(new RatingHistoryPoint(LocalDate.now(), rating.getRating(), null, null, false));
        }
        
        // Build history from most recent backwards
        List<RatingHistoryPoint> history = new ArrayList<>();
        
        // Start with current state
        double currentRating = rating.getRating();
        
        for (int i = 0; i < changes.size(); i++) {
            RatingChange change = changes.get(i);
            history.add(0, new RatingHistoryPoint(
                    change.getMatchDate(),
                    change.getRatingAfter(),
                    change.getOpponentName(),
                    change.getTournamentName(),
                    change.isWon()
            ));
        }
        
        // Add the starting point (before first match in history)
        if (!changes.isEmpty()) {
            RatingChange firstChange = changes.get(changes.size() - 1);
            history.add(0, new RatingHistoryPoint(
                    firstChange.getMatchDate().minusDays(1),
                    firstChange.getRatingBefore(),
                    null,
                    null,
                    false
            ));
        }
        
        return history;
    }

    /**
     * Get statistics about the rating system.
     */
    public RatingStats getStats() {
        long totalPlayers = owlRatingRepository.count();
        long activePlayers = owlRatingRepository.countByMatchesPlayedGreaterThanEqual(5);
        
        List<OwlRatingDocument> all = owlRatingRepository.findAll();
        
        double avgRating = all.stream()
                .filter(r -> r.getMatchesPlayed() >= 5)
                .mapToDouble(OwlRatingDocument::getRating)
                .average()
                .orElse(1500);
        
        double maxRating = all.stream()
                .mapToDouble(OwlRatingDocument::getRating)
                .max()
                .orElse(1500);
        
        double minRating = all.stream()
                .filter(r -> r.getMatchesPlayed() >= 5)
                .mapToDouble(OwlRatingDocument::getRating)
                .min()
                .orElse(1500);

        return new RatingStats(totalPlayers, activePlayers, avgRating, maxRating, minRating);
    }

    // ============ POINT CALCULATION ============

    /**
     * Calculate point exchange for a match.
     */
    public PointExchange calculatePoints(
            FixtureDocument match,
            OddsDocument odds,
            OwlRatingDocument winner,
            OwlRatingDocument loser
    ) {
        // Determine if winner was player 1 (home)
        boolean winnerIsPlayer1 = determineWinnerKey(match).equals(match.getFirstPlayerKey());
        
        // Get odds for winner
        Double winnerOdds = null;
        Double loserOdds = null;
        
        if (odds != null) {
            winnerOdds = winnerIsPlayer1 ? odds.getHomeOdds() : odds.getAwayOdds();
            loserOdds = winnerIsPlayer1 ? odds.getAwayOdds() : odds.getHomeOdds();
        }

        // Calculate expected win probability
        double expectedWinProb;
        if (winnerOdds != null && winnerOdds > 1.0) {
            // Use odds-based probability
            expectedWinProb = 1.0 / winnerOdds;
            // Adjust for bookmaker margin (assume ~5%)
            expectedWinProb = Math.min(0.95, expectedWinProb * 0.95);
        } else {
            // Fallback to rating-based calculation
            expectedWinProb = calculateExpectedProbFromRatings(winner.getRating(), loser.getRating());
            // Estimate what odds would have been
            winnerOdds = 1.0 / expectedWinProb;
            loserOdds = 1.0 / (1 - expectedWinProb);
        }
        
        // Clamp probability to reasonable range
        expectedWinProb = Math.max(0.05, Math.min(0.95, expectedWinProb));

        // Calculate multipliers
        double dominanceMultiplier = calculateDominanceMultiplier(match.getScore(), true);
        double loserDominanceMultiplier = calculateDominanceMultiplier(match.getScore(), false);
        double tournamentMultiplier = getTournamentMultiplier(match.getTournamentKey());

        // Calculate base K with tournament modifier
        double k = BASE_K * tournamentMultiplier;

        // Winner gains more for unexpected wins (low expected prob)
        // Formula: K × (1 - expectedProb) × dominance
        double pointsGained = k * (1 - expectedWinProb) * dominanceMultiplier;

        // Loser loses more for expected wins (high expected prob for winner)
        // Formula: K × expectedProb × dominance
        double pointsLost = k * expectedWinProb * loserDominanceMultiplier;

        // Round to 1 decimal place
        pointsGained = Math.round(pointsGained * 10) / 10.0;
        pointsLost = Math.round(pointsLost * 10) / 10.0;

        return new PointExchange(
                pointsGained,
                pointsLost,
                expectedWinProb,
                winnerOdds,
                loserOdds,
                dominanceMultiplier,
                loserDominanceMultiplier,
                tournamentMultiplier
        );
    }

    /**
     * Calculate expected probability using Elo formula.
     * Used as fallback when odds not available.
     */
    private double calculateExpectedProbFromRatings(double winnerRating, double loserRating) {
        return 1.0 / (1.0 + Math.pow(10, (loserRating - winnerRating) / 400));
    }

    /**
     * Calculate dominance multiplier based on score.
     */
    private double calculateDominanceMultiplier(String score, boolean forWinner) {
        if (score == null || score.isBlank()) return 1.0;

        int[] sets = parseSetCount(score);
        int setsWon = sets[0];
        int setsLost = sets[1];
        int totalSets = setsWon + setsLost;

        if (totalSets == 0) return 1.0;

        if (forWinner) {
            // Winner multipliers - reward dominance
            if (totalSets == 2 && setsLost == 0) return 1.5;   // 2-0 straight sets
            if (totalSets == 3 && setsLost == 0) return 1.6;   // 3-0 straight sets (slam)
            if (totalSets == 3 && setsLost == 1) return 1.2;   // 2-1
            if (totalSets == 4 && setsLost == 1) return 1.2;   // 3-1
            if (totalSets == 5) return 0.85;                    // 3-2 (struggled)
            return 1.0;
        } else {
            // Loser multipliers - reduce penalty for fighting hard
            if (totalSets == 2 && setsWon == 0) return 0.7;    // 0-2 collapsed
            if (totalSets == 3 && setsWon == 0) return 0.6;    // 0-3 collapsed
            if (totalSets == 3 && setsWon == 1) return 1.0;    // 1-2 competitive
            if (totalSets == 4 && setsWon == 1) return 1.1;    // 1-3 fought
            if (totalSets == 5) return 1.4;                     // 2-3 great effort
            return 1.0;
        }
    }

    /**
     * Get tournament importance multiplier.
     */
    private double getTournamentMultiplier(String tournamentKey) {
        if (tournamentKey == null) return 1.0;
        
        String key = tournamentKey.toLowerCase();
        
        // Grand Slams - most important
        for (String gs : GRAND_SLAM_KEYWORDS) {
            if (key.contains(gs)) return 2.0;
        }
        
        // Masters 1000
        for (String m : MASTERS_KEYWORDS) {
            if (key.contains(m)) return 1.5;
        }
        
        // ATP 500 (heuristic - "500" in name or known tournaments)
        if (key.contains("500") || key.contains("barcelona") || 
            key.contains("hamburg") || key.contains("washington")) {
            return 1.25;
        }
        
        // Challenger events
        if (key.contains("challenger")) {
            return 0.75;
        }
        
        // Default for ATP 250 and others
        return 1.0;
    }

    private String getTournamentName(String tournamentKey) {
        if (tournamentKey == null) return "Unknown";
        
        return tournamentRepository.findByTournamentKey(tournamentKey)
                .map(TournamentDocument::getTournamentName)
                .orElse(tournamentKey);
    }

    /**
     * Parse set count from score string.
     * Returns [winner sets, loser sets].
     */
    private int[] parseSetCount(String score) {
        if (score == null) return new int[]{0, 0};
        
        // Handle "X-Y" format (sets summary)
        if (score.matches("^\\d+-\\d+$")) {
            String[] parts = score.split("-");
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        }

        // Parse detailed set scores like "6-3, 7-6, 6-4"
        String[] sets = score.split("[,\\s]+");
        int winnerSets = 0;
        int loserSets = 0;

        for (String set : sets) {
            set = set.trim().replaceAll("\\(\\d+\\)", ""); // Remove tiebreak
            if (set.matches("\\d+-\\d+")) {
                String[] games = set.split("-");
                try {
                    int g1 = Integer.parseInt(games[0]);
                    int g2 = Integer.parseInt(games[1]);
                    if (g1 > g2) winnerSets++;
                    else if (g2 > g1) loserSets++;
                } catch (NumberFormatException ignored) {}
            }
        }

        return new int[]{winnerSets, loserSets};
    }

    private String determineWinnerKey(FixtureDocument match) {
        String winner = match.getWinner();
        if (winner == null) return null;
        
        if (winner.equals(match.getFirstPlayerKey())) return winner;
        if (winner.equals(match.getSecondPlayerKey())) return winner;
        if ("First Player".equals(winner)) return match.getFirstPlayerKey();
        if ("Second Player".equals(winner)) return match.getSecondPlayerKey();
        
        return winner;
    }

    // ============ RESULT RECORDS ============

    public record PointExchange(
            double pointsGained,
            double pointsLost,
            double expectedWinProb,
            Double winnerOdds,
            Double loserOdds,
            double dominanceMultiplier,
            double loserDominanceMultiplier,
            double tournamentMultiplier
    ) {
        public String getExpectedProbFormatted() {
            return String.format("%.0f%%", expectedWinProb * 100);
        }
        
        public String getPointsGainedFormatted() {
            return String.format("+%.1f", pointsGained);
        }
        
        public String getPointsLostFormatted() {
            return String.format("-%.1f", pointsLost);
        }
    }

    public record InitializationResult(
            int matchesProcessed,
            int matchesSkipped,
            int matchesWithOdds,
            long totalPlayers,
            LocalDate startDate,
            LocalDate endDate
    ) {}

    public record RatingStats(
            long totalPlayers,
            long activePlayers,
            double avgRating,
            double maxRating,
            double minRating
    ) {
        public String getAvgRatingFormatted() {
            return String.format("%.1f", avgRating);
        }
        
        public String getMaxRatingFormatted() {
            return String.format("%.1f", maxRating);
        }
        
        public String getMinRatingFormatted() {
            return String.format("%.1f", minRating);
        }
    }

    public record TrendStats(
            int hotCount,
            int risingCount,
            int stableCount,
            int coolingCount,
            int coldCount,
            int totalPlayers,
            double avgMomentum,
            double avgConsistency
    ) {
        public String getAvgMomentumFormatted() {
            return String.format("%+.1f", avgMomentum);
        }
        
        public String getAvgConsistencyFormatted() {
            return String.format("%.1f", avgConsistency);
        }
    }

    public record RatingHistoryPoint(
            LocalDate date,
            double rating,
            String opponentName,
            String tournamentName,
            boolean won
    ) {
        public String getDateFormatted() {
            return date.toString();
        }
        
        public String getRatingFormatted() {
            return String.format("%.1f", rating);
        }
        
        public String getLabel() {
            if (opponentName == null) return "Start";
            return (won ? "W" : "L") + " vs " + opponentName;
        }
    }
}

