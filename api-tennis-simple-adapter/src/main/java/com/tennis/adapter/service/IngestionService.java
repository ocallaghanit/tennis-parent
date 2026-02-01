package com.tennis.adapter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tennis.adapter.client.ApiTennisClient;
import com.tennis.adapter.model.*;
import com.tennis.adapter.repository.*;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for ingesting data from API Tennis and storing in MongoDB.
 * Filters for Men's Singles only (ATP Singles = 265).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    
    /**
     * Maximum reliable date range for API Tennis requests (7 days).
     * Larger ranges may cause 500 errors from the upstream API.
     */
    public static final int MAX_BATCH_DAYS = 7;
    
    /**
     * Player profile TTL - skip fetching if fetched within this many days
     */
    private static final int PLAYER_TTL_DAYS = 14;
    
    /**
     * Delay between player API calls to avoid rate limiting (milliseconds)
     */
    private static final int PLAYER_FETCH_DELAY_MS = 200;
    
    /**
     * Allowed event type keys (for tournament filtering)
     * 265 = ATP Singles
     * 281 = Challenger Men Singles
     */
    private static final Set<String> ALLOWED_EVENT_TYPE_KEYS = Set.of("265", "281");
    
    /**
     * Allowed event type names (for fixture filtering - API returns event_type_type as text)
     * Note: API returns "Atp Singles" not "ATP Singles" (case matters!)
     */
    private static final Set<String> ALLOWED_EVENT_TYPE_NAMES = Set.of(
            "Atp Singles",           // Main ATP Tour (including Grand Slams like Australian Open)
            "Challenger Men Singles" // ATP Challenger Tour
    );

    private final ApiTennisClient apiClient;
    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerRepository playerRepository;
    private final OddsRepository oddsRepository;
    private final H2HRepository h2hRepository;

    public IngestionService(
            ApiTennisClient apiClient,
            EventRepository eventRepository,
            TournamentRepository tournamentRepository,
            FixtureRepository fixtureRepository,
            PlayerRepository playerRepository,
            OddsRepository oddsRepository,
            H2HRepository h2hRepository
    ) {
        this.apiClient = apiClient;
        this.eventRepository = eventRepository;
        this.tournamentRepository = tournamentRepository;
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.oddsRepository = oddsRepository;
        this.h2hRepository = h2hRepository;
    }

    /**
     * Ingest all event types
     */
    public IngestionResult ingestEvents() {
        log.info("Ingesting events...");
        
        try {
            JsonNode response = apiClient.getEvents();
            
            if (!isSuccessful(response)) {
                log.error("Failed to fetch events - API returned unsuccessful response");
                return IngestionResult.apiError("API Tennis returned unsuccessful response for events");
            }

            JsonNode result = response.get("result");
            if (result == null || !result.isArray()) {
                log.warn("No events found in response");
                return IngestionResult.success(0, "No events found in API response");
            }

            int count = 0;
            for (JsonNode eventNode : result) {
                String eventKey = getTextOrNull(eventNode, "event_type_key");
                if (eventKey == null) continue;

                EventDocument doc = eventRepository.findByEventKey(eventKey)
                        .orElse(new EventDocument());
                
                doc.setEventKey(eventKey);
                doc.setEventName(getTextOrNull(eventNode, "event_type_name"));
                doc.setRaw(Document.parse(eventNode.toString()));
                doc.setUpdatedAt(Instant.now());
                
                if (doc.getId() == null) {
                    doc.setId(eventKey);
                }
                
                eventRepository.save(doc);
                count++;
            }

            log.info("Ingested {} events", count);
            return IngestionResult.success(count, "Ingested " + count + " events");
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching events: {} {}", e.getStatusCode(), e.getMessage());
            return IngestionResult.apiError(e.getStatusCode().value(), "Failed to fetch events - " + extractErrorHint(e));
        } catch (Exception e) {
            log.error("Error ingesting events: {}", e.getMessage(), e);
            return IngestionResult.failure("Error ingesting events: " + e.getMessage());
        }
    }

    /**
     * Ingest tournaments, optionally filtered by event type
     */
    public IngestionResult ingestTournaments(String eventTypeKey) {
        log.info("Ingesting tournaments for eventType={}", eventTypeKey);
        
        try {
            JsonNode response = apiClient.getTournaments(eventTypeKey);
            
            if (!isSuccessful(response)) {
                log.error("Failed to fetch tournaments - API returned unsuccessful response");
                return IngestionResult.apiError("API Tennis returned unsuccessful response for tournaments");
            }

            JsonNode result = response.get("result");
            if (result == null || !result.isArray()) {
                log.warn("No tournaments found in response");
                return IngestionResult.success(0, "No tournaments found in API response");
            }

            int count = 0;
            for (JsonNode node : result) {
                String tournamentKey = getTextOrNull(node, "tournament_key");
                if (tournamentKey == null) continue;

                TournamentDocument doc = tournamentRepository.findByTournamentKey(tournamentKey)
                        .orElse(new TournamentDocument());
                
                doc.setTournamentKey(tournamentKey);
                doc.setTournamentName(getTextOrNull(node, "tournament_name"));
                doc.setEventTypeKey(getTextOrNull(node, "event_type_key"));
                // API has typo "tournament_sourface" instead of "surface"
                String surface = getTextOrNull(node, "tournament_sourface");
                if (surface == null) surface = getTextOrNull(node, "surface");
                doc.setSurface(surface);
                doc.setCountry(getTextOrNull(node, "country_name"));
                doc.setRaw(Document.parse(node.toString()));
                doc.setUpdatedAt(Instant.now());
                
                if (doc.getId() == null) {
                    doc.setId(tournamentKey);
                }
                
                tournamentRepository.save(doc);
                count++;
            }

            log.info("Ingested {} tournaments", count);
            return IngestionResult.success(count, "Ingested " + count + " tournaments");
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching tournaments: {} {}", e.getStatusCode(), e.getMessage());
            return IngestionResult.apiError(e.getStatusCode().value(), "Failed to fetch tournaments - " + extractErrorHint(e));
        } catch (Exception e) {
            log.error("Error ingesting tournaments: {}", e.getMessage(), e);
            return IngestionResult.failure("Error ingesting tournaments: " + e.getMessage());
        }
    }

    /**
     * Ingest fixtures by date range
     */
    public IngestionResult ingestFixtures(LocalDate dateStart, LocalDate dateStop) {
        log.info("Ingesting fixtures from {} to {}", dateStart, dateStop);
        
        try {
            JsonNode response = apiClient.getFixturesByDateRange(
                    dateStart.toString(), 
                    dateStop.toString()
            );
            
            return processFixtureResponse(response);
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching fixtures: {} {}", e.getStatusCode(), e.getMessage());
            String hint = extractErrorHint(e);
            if (e.getStatusCode().value() == 500) {
                hint += " (try a smaller date range or wait and retry)";
            }
            return IngestionResult.apiError(e.getStatusCode().value(), "Failed to fetch fixtures - " + hint);
        } catch (Exception e) {
            log.error("Error ingesting fixtures: {}", e.getMessage(), e);
            return IngestionResult.failure("Error ingesting fixtures: " + e.getMessage());
        }
    }

    /**
     * Ingest fixtures by tournament
     */
    public IngestionResult ingestFixturesByTournament(String tournamentKey) {
        log.info("Ingesting fixtures for tournament={}", tournamentKey);
        
        try {
            JsonNode response = apiClient.getFixturesByTournament(tournamentKey);
            return processFixtureResponse(response);
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching fixtures for tournament {}: {} {}", 
                    tournamentKey, e.getStatusCode(), e.getMessage());
            return IngestionResult.apiError(e.getStatusCode().value(), 
                    "Failed to fetch fixtures for tournament " + tournamentKey + " - " + extractErrorHint(e));
        } catch (Exception e) {
            log.error("Error ingesting fixtures for tournament {}: {}", tournamentKey, e.getMessage(), e);
            return IngestionResult.failure("Error ingesting fixtures: " + e.getMessage());
        }
    }

    /**
     * Ingest fixtures by date range with automatic 7-day batching.
     * Large date ranges are automatically split into smaller chunks for reliability.
     */
    public IngestionResult ingestFixturesBatched(LocalDate dateStart, LocalDate dateStop) {
        long totalDays = ChronoUnit.DAYS.between(dateStart, dateStop) + 1;
        
        // If within limit, use single call
        if (totalDays <= MAX_BATCH_DAYS) {
            return ingestFixtures(dateStart, dateStop);
        }
        
        log.info("Batched ingestion: {} to {} ({} days, splitting into {}-day batches)", 
                dateStart, dateStop, totalDays, MAX_BATCH_DAYS);
        
        int totalCount = 0;
        int batchNumber = 0;
        int totalBatches = (int) Math.ceil((double) totalDays / MAX_BATCH_DAYS);
        List<String> errors = new ArrayList<>();
        
        LocalDate current = dateStart;
        while (!current.isAfter(dateStop)) {
            batchNumber++;
            LocalDate batchEnd = current.plusDays(MAX_BATCH_DAYS - 1);
            if (batchEnd.isAfter(dateStop)) {
                batchEnd = dateStop;
            }
            
            log.info("Fixtures batch {}/{}: {} to {}", batchNumber, totalBatches, current, batchEnd);
            
            IngestionResult batchResult = ingestFixtures(current, batchEnd);
            
            if (batchResult.isSuccess()) {
                totalCount += batchResult.getCount();
            } else {
                errors.add(String.format("Batch %d (%s to %s): %s", 
                        batchNumber, current, batchEnd, batchResult.getMessage()));
            }
            
            current = batchEnd.plusDays(1);
        }
        
        if (errors.isEmpty()) {
            return IngestionResult.success(totalCount, 
                    String.format("Ingested %d fixtures in %d batches", totalCount, totalBatches));
        } else if (totalCount > 0) {
            return IngestionResult.partialSuccess(totalCount,
                    String.format("Partial success: %d fixtures ingested, %d batch errors: %s", 
                            totalCount, errors.size(), String.join("; ", errors)));
        } else {
            return IngestionResult.failure("All batches failed: " + String.join("; ", errors));
        }
    }

    private IngestionResult processFixtureResponse(JsonNode response) {
        if (!isSuccessful(response)) {
            log.error("Failed to fetch fixtures - API returned unsuccessful response");
            return IngestionResult.apiError("API Tennis returned unsuccessful response for fixtures");
        }

        JsonNode result = response.get("result");
        if (result == null || !result.isArray()) {
            log.warn("No fixtures found in response");
            return IngestionResult.success(0, "No fixtures found in API response");
        }

        int count = 0;
        int skippedNotSingles = 0;
        Set<String> newPlayerKeys = new HashSet<>(); // Only track players from NEW fixtures

        for (JsonNode node : result) {
            String eventKey = getTextOrNull(node, "event_key");
            if (eventKey == null) continue;
            
            // Filter: Only process Men's Singles (ATP + Challenger)
            // API returns event_type_type as text (e.g., "ATP Singles", "Challenger Men Singles")
            String eventTypeName = getTextOrNull(node, "event_type_type");
            if (eventTypeName == null || !ALLOWED_EVENT_TYPE_NAMES.contains(eventTypeName)) {
                skippedNotSingles++;
                continue;
            }

            // Check if this is a NEW fixture (not an update)
            var existingFixture = fixtureRepository.findByEventKey(eventKey);
            boolean isNewFixture = existingFixture.isEmpty();
            
            FixtureDocument doc = existingFixture.orElse(new FixtureDocument());
            
            doc.setEventKey(eventKey);
            doc.setTournamentKey(getTextOrNull(node, "tournament_key"));
            doc.setEventTypeKey(eventTypeName); // Store the type name for reference
            
            String dateStr = getTextOrNull(node, "event_date");
            if (dateStr != null && !dateStr.isEmpty()) {
                try {
                    doc.setEventDate(LocalDate.parse(dateStr));
                } catch (Exception e) {
                    log.warn("Could not parse date: {}", dateStr);
                }
            }
            
            // Player keys can be in different field names depending on API version
            String firstPlayerKey = getTextOrNull(node, "first_player_key");
            if (firstPlayerKey == null) firstPlayerKey = getTextOrNull(node, "event_first_player_key");
            doc.setFirstPlayerKey(firstPlayerKey);
            doc.setFirstPlayerName(getTextOrNull(node, "event_first_player"));
            
            String secondPlayerKey = getTextOrNull(node, "second_player_key");
            if (secondPlayerKey == null) secondPlayerKey = getTextOrNull(node, "event_second_player_key");
            doc.setSecondPlayerKey(secondPlayerKey);
            doc.setSecondPlayerName(getTextOrNull(node, "event_second_player"));
            doc.setStatus(getTextOrNull(node, "event_status"));
            
            // Convert "First Player"/"Second Player" to actual player key
            String winnerRaw = getTextOrNull(node, "event_winner");
            if ("First Player".equalsIgnoreCase(winnerRaw)) {
                doc.setWinner(firstPlayerKey);
            } else if ("Second Player".equalsIgnoreCase(winnerRaw)) {
                doc.setWinner(secondPlayerKey);
            } else {
                doc.setWinner(winnerRaw); // Keep as-is if different format
            }
            
            doc.setScore(getTextOrNull(node, "event_final_result"));
            doc.setRaw(Document.parse(node.toString()));
            doc.setUpdatedAt(Instant.now());
            
            if (doc.getId() == null) {
                doc.setId(eventKey);
            }
            
            fixtureRepository.save(doc);
            count++;

            // Only collect player keys from NEW fixtures (not updates)
            if (isNewFixture) {
                if (firstPlayerKey != null && !firstPlayerKey.isBlank()) {
                    newPlayerKeys.add(firstPlayerKey);
                }
                if (secondPlayerKey != null && !secondPlayerKey.isBlank()) {
                    newPlayerKeys.add(secondPlayerKey);
                }
            }
        }

        log.info("Ingested {} Men's Singles fixtures (skipped {} non-singles)", count, skippedNotSingles);
        
        // Auto-sync players from NEW fixtures (inline, efficient)
        int playersSynced = 0;
        if (!newPlayerKeys.isEmpty()) {
            playersSynced = syncNewPlayers(newPlayerKeys);
        }
        
        String message = String.format("Ingested %d Men's Singles fixtures (filtered %d non-singles)", count, skippedNotSingles);
        if (playersSynced > 0) {
            message += String.format(", synced %d new players", playersSynced);
        }
        return IngestionResult.success(count, message);
    }
    
    /**
     * Sync only new/unknown players from a set of player keys.
     * Skips players that already exist in the database.
     * Rate-limited to avoid API throttling.
     * 
     * @param playerKeys Set of player keys to potentially sync
     * @return Number of players actually fetched
     */
    private int syncNewPlayers(Set<String> playerKeys) {
        if (playerKeys.isEmpty()) return 0;
        
        // Filter out players we already have
        Set<String> unknownPlayers = new HashSet<>();
        for (String key : playerKeys) {
            if (playerRepository.findByPlayerKey(key).isEmpty()) {
                unknownPlayers.add(key);
            }
        }
        
        if (unknownPlayers.isEmpty()) {
            log.debug("All {} players already in database, skipping sync", playerKeys.size());
            return 0;
        }
        
        log.info("Syncing {} new players (out of {} total from fixtures)", unknownPlayers.size(), playerKeys.size());
        
        int fetched = 0;
        int failed = 0;
        
        for (String playerKey : unknownPlayers) {
            try {
                JsonNode response = apiClient.getPlayer(playerKey);
                
                if (isSuccessful(response)) {
                    JsonNode result = response.get("result");
                    if (result != null && result.isArray() && result.size() > 0) {
                        JsonNode node = result.get(0);
                        
                        PlayerDocument doc = new PlayerDocument();
                        doc.setId(playerKey);
                        doc.setPlayerKey(playerKey);
                        doc.setPlayerName(getTextOrNull(node, "player_name"));
                        doc.setCountry(getTextOrNull(node, "player_country"));
                        doc.setHand(getTextOrNull(node, "player_hand"));
                        
                        // Extract current singles rank from stats array
                        Integer rank = extractCurrentSinglesRank(node);
                        doc.setCurrentRank(rank);
                        
                        doc.setRaw(Document.parse(node.toString()));
                        doc.setUpdatedAt(Instant.now());
                        doc.setFetchedAt(Instant.now());
                        
                        playerRepository.save(doc);
                        fetched++;
                    }
                } else {
                    failed++;
                }
            } catch (Exception e) {
                log.warn("Failed to fetch player {}: {}", playerKey, e.getMessage());
                failed++;
            }
            
            // Rate limiting - 200ms between calls
            if (fetched + failed < unknownPlayers.size()) {
                try {
                    Thread.sleep(PLAYER_FETCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("Player sync complete: fetched {}, failed {}", fetched, failed);
        return fetched;
    }

    /**
     * Ingest a single player
     */
    public IngestionResult ingestPlayer(String playerKey) {
        return ingestPlayer(playerKey, false);
    }

    /**
     * Ingest a single player with optional force re-fetch
     */
    public IngestionResult ingestPlayer(String playerKey, boolean force) {
        log.info("Ingesting player={}, force={}", playerKey, force);
        
        try {
            // Check if already exists and was fetched recently (unless force=true)
            var existing = playerRepository.findByPlayerKey(playerKey);
            if (!force && existing.isPresent()) {
                Instant lastFetch = existing.get().getFetchedAt();
                if (lastFetch != null && lastFetch.isAfter(Instant.now().minusSeconds(86400))) {
                    log.debug("Player {} already fetched within 24 hours, skipping", playerKey);
                    return IngestionResult.success(0, "Player already up-to-date (fetched within 24 hours)");
                }
            }

            JsonNode response = apiClient.getPlayer(playerKey);
            
            if (!isSuccessful(response)) {
                log.error("Failed to fetch player {} - API returned unsuccessful response", playerKey);
                return IngestionResult.apiError("API Tennis returned unsuccessful response for player " + playerKey);
            }

            JsonNode result = response.get("result");
            if (result == null || !result.isArray() || result.size() == 0) {
                log.warn("No player data found for {}", playerKey);
                return IngestionResult.success(0, "No player data found for " + playerKey);
            }

            JsonNode node = result.get(0);
            
            PlayerDocument doc = existing.orElse(new PlayerDocument());
            doc.setPlayerKey(playerKey);
            doc.setPlayerName(getTextOrNull(node, "player_name"));
            doc.setCountry(getTextOrNull(node, "player_country"));
            doc.setHand(getTextOrNull(node, "player_hand"));
            
            // Extract current singles rank from stats array
            Integer rank = extractCurrentSinglesRank(node);
            doc.setCurrentRank(rank);
            
            doc.setRaw(Document.parse(node.toString()));
            doc.setUpdatedAt(Instant.now());
            
            if (doc.getId() == null) {
                doc.setId(playerKey);
            }
            
            playerRepository.save(doc);
            String playerName = doc.getPlayerName() != null ? doc.getPlayerName() : playerKey;
            log.info("Ingested player: {} ({})", playerName, playerKey);
            return IngestionResult.success(1, "Ingested player: " + playerName);
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching player {}: {} {}", playerKey, e.getStatusCode(), e.getMessage());
            return IngestionResult.apiError(e.getStatusCode().value(), 
                    "Failed to fetch player " + playerKey + " - " + extractErrorHint(e));
        } catch (Exception e) {
            log.error("Error ingesting player {}: {}", playerKey, e.getMessage(), e);
            return IngestionResult.failure("Error ingesting player: " + e.getMessage());
        }
    }

    /**
     * Sync players from stored fixtures.
     * Extracts unique player keys from all fixtures and fetches missing/stale profiles.
     * Rate-limited to avoid API throttling.
     */
    public IngestionResult syncPlayersFromFixtures() {
        return syncPlayersFromFixtures(false);
    }

    /**
     * Sync players from stored fixtures with optional force refresh.
     * Extracts unique player keys from all fixtures and fetches missing/stale profiles.
     * Rate-limited to avoid API throttling.
     * @param force If true, re-fetch all players regardless of TTL
     */
    public IngestionResult syncPlayersFromFixtures(boolean force) {
        log.info("Starting player sync from fixtures (force={})...", force);
        
        try {
            // Step 1: Extract all unique player keys from fixtures
            List<FixtureDocument> allFixtures = fixtureRepository.findAll();
            Set<String> playerKeys = new HashSet<>();
            
            for (FixtureDocument fixture : allFixtures) {
                if (fixture.getFirstPlayerKey() != null && !fixture.getFirstPlayerKey().isBlank()) {
                    playerKeys.add(fixture.getFirstPlayerKey());
                }
                if (fixture.getSecondPlayerKey() != null && !fixture.getSecondPlayerKey().isBlank()) {
                    playerKeys.add(fixture.getSecondPlayerKey());
                }
            }
            
            if (playerKeys.isEmpty()) {
                log.info("No player keys found in fixtures");
                return IngestionResult.success(0, "No players to sync - no fixtures found");
            }
            
            log.info("Found {} unique players in {} fixtures", playerKeys.size(), allFixtures.size());
            
            // Step 2: Filter out players that are already up-to-date (unless force=true)
            int skippedCount = 0;
            if (!force) {
                Instant ttlThreshold = Instant.now().minus(PLAYER_TTL_DAYS, ChronoUnit.DAYS);
                
                // Use lightweight projection query - only fetch playerKey field
                List<PlayerDocument> upToDatePlayers = playerRepository.findPlayerKeysByFetchedAtAfter(ttlThreshold);
                Set<String> upToDateKeys = upToDatePlayers.stream()
                        .map(PlayerDocument::getPlayerKey)
                        .collect(Collectors.toSet());
                
                // Remove up-to-date players from fetch list
                playerKeys.removeAll(upToDateKeys);
                skippedCount = upToDateKeys.size();
                
                if (playerKeys.isEmpty()) {
                    log.info("All {} players are up-to-date (fetched within {} days)", 
                            skippedCount, PLAYER_TTL_DAYS);
                    return IngestionResult.success(0, 
                            String.format("All %d players already up-to-date", skippedCount));
                }
                
                log.info("Need to fetch {} players ({} already up-to-date)", 
                        playerKeys.size(), skippedCount);
            } else {
                log.info("Force mode enabled - will re-fetch all {} players", playerKeys.size());
            }
            
            // Step 3: Fetch missing/stale players with rate limiting
            int fetched = 0;
            int failed = 0;
            int total = playerKeys.size();
            int processed = 0;
            
            for (String playerKey : playerKeys) {
                processed++;
                
                try {
                    JsonNode response = apiClient.getPlayer(playerKey);
                    
                    if (isSuccessful(response)) {
                        JsonNode result = response.get("result");
                        if (result != null && result.isArray() && result.size() > 0) {
                            JsonNode node = result.get(0);
                            
                            PlayerDocument doc = playerRepository.findByPlayerKey(playerKey)
                                    .orElse(new PlayerDocument());
                            
                            doc.setPlayerKey(playerKey);
                            doc.setPlayerName(getTextOrNull(node, "player_name"));
                            doc.setCountry(getTextOrNull(node, "player_country"));
                            doc.setHand(getTextOrNull(node, "player_hand"));
                            
                            // Extract current singles rank from stats array
                            Integer rank = extractCurrentSinglesRank(node);
                            doc.setCurrentRank(rank);
                            
                            doc.setRaw(Document.parse(node.toString()));
                            doc.setUpdatedAt(Instant.now());
                            doc.setFetchedAt(Instant.now());
                            
                            if (doc.getId() == null) {
                                doc.setId(playerKey);
                            }
                            
                            playerRepository.save(doc);
                            fetched++;
                        }
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to fetch player {}: {}", playerKey, e.getMessage());
                    failed++;
                }
                
                // Progress logging every 50 players
                if (processed % 50 == 0 || processed == total) {
                    log.info("Player sync progress: {}/{} (fetched: {}, failed: {})", 
                            processed, total, fetched, failed);
                }
                
                // Rate limiting delay
                if (processed < total) {
                    try {
                        Thread.sleep(PLAYER_FETCH_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            String message = String.format("Synced %d players (fetched: %d, failed: %d, skipped %d up-to-date)", 
                    total, fetched, failed, skippedCount);
            log.info(message);
            
            if (failed > 0 && fetched > 0) {
                return IngestionResult.partialSuccess(fetched, message);
            }
            return IngestionResult.success(fetched, message);
            
        } catch (Exception e) {
            log.error("Error syncing players: {}", e.getMessage(), e);
            return IngestionResult.failure("Error syncing players: " + e.getMessage());
        }
    }

    /**
     * Ingest odds by date range.
     * Fetches odds for both ATP Singles (265) and Challenger Men Singles (281).
     * Handles both array format [{match_key: "123", ...}] and object format {"123": {...}}
     */
    public IngestionResult ingestOdds(LocalDate dateStart, LocalDate dateStop) {
        log.info("Ingesting odds from {} to {}", dateStart, dateStop);
        
        int totalCount = 0;
        List<String> errors = new ArrayList<>();
        
        // Fetch odds for both ATP Singles and Challenger Men Singles
        String[] eventTypes = {"265", "281"}; // ATP Singles, Challenger Men Singles
        String[] eventTypeNames = {"ATP Singles", "Challenger Men Singles"};
        
        for (int i = 0; i < eventTypes.length; i++) {
            String eventType = eventTypes[i];
            String eventTypeName = eventTypeNames[i];
            
            try {
                log.info("Fetching odds for {} ({}) from {} to {}", eventTypeName, eventType, dateStart, dateStop);
                
                JsonNode response = apiClient.getOddsByDateRange(
                        dateStart.toString(),
                        dateStop.toString(),
                        eventType
                );
                
                if (!isSuccessful(response)) {
                    // Log the actual response for debugging
                    String errorDetail = response != null ? response.toString() : "null response";
                    if (errorDetail.length() > 200) {
                        errorDetail = errorDetail.substring(0, 200) + "...";
                    }
                    log.warn("No odds available for {} ({}) - API response: {}", eventTypeName, eventType, errorDetail);
                    // Don't fail completely, just log and continue to next event type
                    continue;
                }

                JsonNode result = response.get("result");
                if (result == null) {
                    log.debug("No odds result for {} ({})", eventTypeName, eventType);
                    continue;
                }

                int count = processOddsResult(result);
                totalCount += count;
                log.info("Processed {} odds records for {} ({})", count, eventTypeName, eventType);
                
            } catch (Exception e) {
                log.error("Error fetching odds for {} ({}): {}", eventTypeName, eventType, e.getMessage());
                errors.add(eventTypeName + ": " + e.getMessage());
            }
        }
        
        if (totalCount == 0 && !errors.isEmpty()) {
            return IngestionResult.apiError("Failed to fetch odds: " + String.join("; ", errors));
        }
        
        return IngestionResult.success(totalCount, 
                String.format("Ingested %d odds records from %s to %s", totalCount, dateStart, dateStop));
    }
    
    /**
     * Process odds result from API response.
     * Handles both array format [{match_key: "123", odds: {...}}, ...]
     * and object format {"match_key_123": {...}, "match_key_456": {...}}
     */
    private int processOddsResult(JsonNode result) {
        int count = 0;
        
        // Handle array format: [{match_key: "123", odds: {...}}, ...]
        if (result.isArray()) {
            log.debug("Processing odds in array format, {} items", result.size());
            for (JsonNode node : result) {
                String matchKey = getTextOrNull(node, "match_key");
                if (matchKey == null) matchKey = getTextOrNull(node, "event_key");
                if (matchKey == null) continue;
                
                if (saveOddsDocument(matchKey, node)) {
                    count++;
                }
            }
        }
        // Handle object format: {"match_key_123": {...}, "match_key_456": {...}}
        else if (result.isObject()) {
            log.debug("Processing odds in object format");
            java.util.Iterator<String> matchKeys = result.fieldNames();
            while (matchKeys.hasNext()) {
                String matchKey = matchKeys.next();
                JsonNode node = result.get(matchKey);
                if (node == null) continue;
                
                if (saveOddsDocument(matchKey, node)) {
                    count++;
                }
            }
        } else {
            log.warn("Unexpected odds response format: neither array nor object");
        }
        
        return count;
    }
    
    /**
     * Save or update an odds document
     * @return true if saved successfully
     */
    private boolean saveOddsDocument(String matchKey, JsonNode node) {
        if (matchKey == null || matchKey.isBlank()) return false;
        
        try {
            OddsDocument doc = oddsRepository.findByMatchKey(matchKey)
                    .orElse(new OddsDocument());
            
            doc.setMatchKey(matchKey);
            doc.setTournamentKey(getTextOrNull(node, "tournament_key"));
            
            String dateStr = getTextOrNull(node, "event_date");
            if (dateStr != null && !dateStr.isEmpty()) {
                try {
                    doc.setEventDate(LocalDate.parse(dateStr));
                } catch (Exception e) {
                    log.warn("Could not parse date: {}", dateStr);
                }
            }
            
            doc.setRaw(Document.parse(node.toString()));
            doc.setUpdatedAt(Instant.now());
            
            if (doc.getId() == null) {
                doc.setId("odds_" + matchKey);
            }
            
            oddsRepository.save(doc);
            return true;
        } catch (Exception e) {
            log.warn("Failed to save odds for match {}: {}", matchKey, e.getMessage());
            return false;
        }
    }

    /**
     * Ingest odds for a single match
     */
    public IngestionResult ingestOddsForMatch(String matchKey) {
        log.info("Ingesting odds for match={}", matchKey);
        
        try {
            var existing = oddsRepository.findByMatchKey(matchKey);
            if (existing.isPresent()) {
                log.debug("Odds for match {} already exist", matchKey);
                return IngestionResult.success(0, "Odds already exist for match " + matchKey);
            }

            JsonNode response = apiClient.getOdds(matchKey);
            
            if (!isSuccessful(response)) {
                log.error("Failed to fetch odds for match {} - API returned unsuccessful response", matchKey);
                return IngestionResult.apiError("API Tennis returned unsuccessful response for match " + matchKey);
            }

            JsonNode result = response.get("result");
            if (result == null) {
                log.warn("No odds data for match {}", matchKey);
                return IngestionResult.success(0, "No odds data available for match " + matchKey);
            }

            OddsDocument doc = new OddsDocument();
            doc.setId("odds_" + matchKey);
            doc.setMatchKey(matchKey);
            doc.setRaw(Document.parse(result.toString()));
            doc.setUpdatedAt(Instant.now());
            
            oddsRepository.save(doc);
            log.info("Ingested odds for match {}", matchKey);
            return IngestionResult.success(1, "Ingested odds for match " + matchKey);
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching odds for match {}: {} {}", 
                    matchKey, e.getStatusCode(), e.getMessage());
            return IngestionResult.apiError(e.getStatusCode().value(), 
                    "Failed to fetch odds for match " + matchKey + " - " + extractErrorHint(e));
        } catch (Exception e) {
            log.error("Error ingesting odds for match {}: {}", matchKey, e.getMessage(), e);
            return IngestionResult.failure("Error ingesting odds: " + e.getMessage());
        }
    }

    /**
     * Ingest odds by date range with automatic 7-day batching.
     * Large date ranges are automatically split into smaller chunks for reliability.
     */
    public IngestionResult ingestOddsBatched(LocalDate dateStart, LocalDate dateStop) {
        long totalDays = ChronoUnit.DAYS.between(dateStart, dateStop) + 1;
        
        // If within limit, use single call
        if (totalDays <= MAX_BATCH_DAYS) {
            return ingestOdds(dateStart, dateStop);
        }
        
        log.info("Batched odds ingestion: {} to {} ({} days, splitting into {}-day batches)", 
                dateStart, dateStop, totalDays, MAX_BATCH_DAYS);
        
        int totalCount = 0;
        int batchNumber = 0;
        int totalBatches = (int) Math.ceil((double) totalDays / MAX_BATCH_DAYS);
        List<String> errors = new ArrayList<>();
        
        LocalDate current = dateStart;
        while (!current.isAfter(dateStop)) {
            batchNumber++;
            LocalDate batchEnd = current.plusDays(MAX_BATCH_DAYS - 1);
            if (batchEnd.isAfter(dateStop)) {
                batchEnd = dateStop;
            }
            
            log.info("Odds batch {}/{}: {} to {}", batchNumber, totalBatches, current, batchEnd);
            
            IngestionResult batchResult = ingestOdds(current, batchEnd);
            
            if (batchResult.isSuccess()) {
                totalCount += batchResult.getCount();
            } else {
                errors.add(String.format("Batch %d (%s to %s): %s", 
                        batchNumber, current, batchEnd, batchResult.getMessage()));
            }
            
            current = batchEnd.plusDays(1);
        }
        
        if (errors.isEmpty()) {
            return IngestionResult.success(totalCount, 
                    String.format("Ingested %d odds records in %d batches", totalCount, totalBatches));
        } else if (totalCount > 0) {
            return IngestionResult.partialSuccess(totalCount,
                    String.format("Partial success: %d odds records ingested, %d batch errors: %s", 
                            totalCount, errors.size(), String.join("; ", errors)));
        } else {
            return IngestionResult.failure("All batches failed: " + String.join("; ", errors));
        }
    }

    /**
     * Cleanup: Remove all non-Men's Singles fixtures from the database.
     * Also removes associated odds and orphaned players.
     */
    public IngestionResult cleanupNonSinglesData() {
        log.info("Cleaning up non-Men's Singles data...");
        
        try {
            List<FixtureDocument> allFixtures = fixtureRepository.findAll();
            int removedFixtures = 0;
            Set<String> removedEventKeys = new HashSet<>();
            
            for (FixtureDocument fixture : allFixtures) {
                String eventTypeName = fixture.getEventTypeKey(); // This now stores the type name
                boolean isNonSingles = eventTypeName == null || !ALLOWED_EVENT_TYPE_NAMES.contains(eventTypeName);
                
                // Also check player names for "/" which indicates doubles
                String p1 = fixture.getFirstPlayerName();
                String p2 = fixture.getSecondPlayerName();
                boolean looksLikeDoubles = (p1 != null && p1.contains("/")) || (p2 != null && p2.contains("/"));
                
                if (isNonSingles || looksLikeDoubles) {
                    removedEventKeys.add(fixture.getEventKey());
                    fixtureRepository.delete(fixture);
                    removedFixtures++;
                }
            }
            
            // Remove odds for deleted fixtures
            int removedOdds = 0;
            for (String eventKey : removedEventKeys) {
                var odds = oddsRepository.findByMatchKey(eventKey);
                if (odds.isPresent()) {
                    oddsRepository.delete(odds.get());
                    removedOdds++;
                }
            }
            
            String message = String.format("Cleaned up %d non-singles fixtures and %d odds records", 
                    removedFixtures, removedOdds);
            log.info(message);
            return IngestionResult.success(removedFixtures, message);
            
        } catch (Exception e) {
            log.error("Error cleaning up non-singles data: {}", e.getMessage(), e);
            return IngestionResult.failure("Error cleaning up: " + e.getMessage());
        }
    }

    // Helper methods
    
    private boolean isSuccessful(JsonNode response) {
        return response != null && 
               response.has("success") && 
               response.get("success").asInt() == 1;
    }

    /**
     * Extract the current singles rank from a player's stats array.
     * Looks for the most recent season with type "singles" and returns its rank.
     */
    private Integer extractCurrentSinglesRank(JsonNode playerNode) {
        if (playerNode == null || !playerNode.has("stats")) {
            return null;
        }
        
        JsonNode stats = playerNode.get("stats");
        if (!stats.isArray() || stats.isEmpty()) {
            return null;
        }
        
        // Find the most recent singles entry
        String mostRecentSeason = null;
        String mostRecentRank = null;
        
        for (JsonNode stat : stats) {
            String type = getTextOrNull(stat, "type");
            String season = getTextOrNull(stat, "season");
            String rank = getTextOrNull(stat, "rank");
            
            // Only consider singles entries with a valid rank
            if ("singles".equalsIgnoreCase(type) && rank != null && !rank.isEmpty()) {
                // Compare seasons (they are year strings like "2024", "2025")
                if (mostRecentSeason == null || (season != null && season.compareTo(mostRecentSeason) > 0)) {
                    mostRecentSeason = season;
                    mostRecentRank = rank;
                }
            }
        }
        
        if (mostRecentRank != null) {
            try {
                return Integer.parseInt(mostRecentRank);
            } catch (NumberFormatException ignored) {
                log.debug("Could not parse rank: {}", mostRecentRank);
            }
        }
        
        return null;
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value.isNull()) return null;
        String text = value.asText();
        return (text == null || text.isEmpty() || "null".equals(text)) ? null : text;
    }

    /**
     * Extract a user-friendly hint from an API error
     */
    private String extractErrorHint(WebClientResponseException e) {
        int status = e.getStatusCode().value();
        switch (status) {
            case 429:
                return "rate limit exceeded - wait a few minutes before retrying";
            case 500:
                return "API server error";
            case 502:
            case 503:
            case 504:
                return "API service temporarily unavailable";
            case 401:
            case 403:
                return "authentication error - check API key";
            case 404:
                return "endpoint not found";
            default:
                return "HTTP " + status;
        }
    }

    // ==================== H2H Methods ====================

    /**
     * H2H TTL - how long before we refresh H2H data (hours)
     */
    private static final int H2H_TTL_HOURS = 24;

    /**
     * Fetch head-to-head data between two players from API.
     * Returns the stored/cached H2H document, or fetches fresh if stale/missing.
     * 
     * @param playerKey1 First player key
     * @param playerKey2 Second player key
     * @param force If true, always fetch fresh data from API
     * @return H2HDocument with match history, or null if fetch fails
     */
    public H2HDocument fetchH2H(String playerKey1, String playerKey2, boolean force) {
        if (playerKey1 == null || playerKey2 == null) {
            return null;
        }

        String h2hId = H2HDocument.createId(playerKey1, playerKey2);
        
        // Check cache first
        var existing = h2hRepository.findById(h2hId);
        if (!force && existing.isPresent()) {
            H2HDocument cached = existing.get();
            // Check if still fresh
            if (cached.getLastFetched() != null && 
                cached.getLastFetched().isAfter(Instant.now().minus(H2H_TTL_HOURS, ChronoUnit.HOURS))) {
                log.debug("Using cached H2H data for {} vs {}", playerKey1, playerKey2);
                return cached;
            }
        }

        // Fetch from API
        log.info("Fetching H2H from API for {} vs {}", playerKey1, playerKey2);
        try {
            JsonNode response = apiClient.getH2H(playerKey1, playerKey2);
            
            if (!isSuccessful(response)) {
                log.warn("Failed to fetch H2H - API returned unsuccessful response");
                return existing.orElse(null);
            }

            JsonNode result = response.get("result");
            if (result == null) {
                log.warn("No H2H result in response");
                return existing.orElse(null);
            }

            // Parse H2H data
            H2HDocument doc = existing.orElse(new H2HDocument());
            doc.setId(h2hId);
            
            // Ensure player keys are stored in consistent order
            if (playerKey1.compareTo(playerKey2) < 0) {
                doc.setPlayerKey1(playerKey1);
                doc.setPlayerKey2(playerKey2);
            } else {
                doc.setPlayerKey1(playerKey2);
                doc.setPlayerKey2(playerKey1);
            }

            // Parse H2H matches to get player names and count wins
            // Note: API returns fields with "event_" prefix (e.g., event_winner, event_first_player)
            JsonNode h2hMatches = result.get("H2H");
            if (h2hMatches != null && h2hMatches.isArray()) {
                int p1Wins = 0;
                int p2Wins = 0;
                
                for (JsonNode match : h2hMatches) {
                    String firstPlayerKey = getTextOrNull(match, "first_player_key");
                    String secondPlayerKey = getTextOrNull(match, "second_player_key");
                    // API uses "event_winner" field
                    String winner = getTextOrNull(match, "event_winner");
                    
                    // Get player names from first match if not set
                    // API uses "event_first_player" and "event_second_player"
                    if (doc.getPlayer1Name() == null || doc.getPlayer2Name() == null) {
                        String firstName = getTextOrNull(match, "event_first_player");
                        String secondName = getTextOrNull(match, "event_second_player");
                        
                        if (playerKey1.equals(firstPlayerKey)) {
                            doc.setPlayer1Name(firstName);
                            doc.setPlayer2Name(secondName);
                        } else {
                            doc.setPlayer1Name(secondName);
                            doc.setPlayer2Name(firstName);
                        }
                    }
                    
                    // Count wins - winner can be player key or "First Player"/"Second Player"
                    String winnerKey = null;
                    if ("First Player".equals(winner)) {
                        winnerKey = firstPlayerKey;
                    } else if ("Second Player".equals(winner)) {
                        winnerKey = secondPlayerKey;
                    } else {
                        winnerKey = winner;
                    }
                    
                    if (doc.getPlayerKey1().equals(winnerKey)) {
                        p1Wins++;
                    } else if (doc.getPlayerKey2().equals(winnerKey)) {
                        p2Wins++;
                    }
                }
                
                doc.setPlayer1Wins(p1Wins);
                doc.setPlayer2Wins(p2Wins);
            }

            // Store the raw response
            doc.setRaw(Document.parse(result.toString()));
            doc.setLastFetched(Instant.now());
            doc.setUpdatedAt(Instant.now());

            h2hRepository.save(doc);
            log.info("Saved H2H data for {} vs {}: {}–{}", 
                    doc.getPlayer1Name(), doc.getPlayer2Name(), 
                    doc.getPlayer1Wins(), doc.getPlayer2Wins());

            return doc;

        } catch (WebClientResponseException e) {
            log.error("API error fetching H2H: {}", e.getMessage());
            return existing.orElse(null);
        } catch (Exception e) {
            log.error("Error fetching H2H: {}", e.getMessage());
            return existing.orElse(null);
        }
    }

    /**
     * Get H2H record as it was BEFORE a specific match date.
     * This is useful for showing the H2H before a match was played.
     * 
     * @param playerKey1 First player key
     * @param playerKey2 Second player key
     * @param beforeDate Only count matches before this date
     * @return Array of [player1Wins, player2Wins] before the given date
     */
    public int[] getH2HBeforeDate(String playerKey1, String playerKey2, LocalDate beforeDate) {
        H2HDocument h2h = fetchH2H(playerKey1, playerKey2, false);
        if (h2h == null || h2h.getRaw() == null) {
            return new int[]{0, 0};
        }

        // Parse H2H matches and filter by date
        Object h2hMatchesObj = h2h.getRaw().get("H2H");
        if (!(h2hMatchesObj instanceof List)) {
            return new int[]{h2h.getPlayer1Wins(), h2h.getPlayer2Wins()};
        }

        @SuppressWarnings("unchecked")
        List<Document> h2hMatches = (List<Document>) h2hMatchesObj;
        
        int p1Wins = 0;
        int p2Wins = 0;
        
        // Normalize player key order
        String pk1 = playerKey1.compareTo(playerKey2) < 0 ? playerKey1 : playerKey2;
        String pk2 = playerKey1.compareTo(playerKey2) < 0 ? playerKey2 : playerKey1;

        for (Document match : h2hMatches) {
            // Parse match date (API uses "event_date")
            String dateStr = match.getString("event_date");
            if (dateStr == null) continue;
            
            try {
                LocalDate matchDate = LocalDate.parse(dateStr.substring(0, 10));
                
                // Only count matches BEFORE the given date
                if (!matchDate.isBefore(beforeDate)) {
                    continue;
                }
                
                // API stores keys as Integer, need to convert
                Object firstPlayerKeyObj = match.get("first_player_key");
                Object secondPlayerKeyObj = match.get("second_player_key");
                String firstPlayerKey = firstPlayerKeyObj != null ? String.valueOf(firstPlayerKeyObj) : null;
                String secondPlayerKey = secondPlayerKeyObj != null ? String.valueOf(secondPlayerKeyObj) : null;
                
                // API uses "event_winner" field
                String winner = match.getString("event_winner");
                
                // Determine winner key
                String winnerKey = null;
                if ("First Player".equals(winner)) {
                    winnerKey = firstPlayerKey;
                } else if ("Second Player".equals(winner)) {
                    winnerKey = secondPlayerKey;
                } else {
                    winnerKey = winner;
                }
                
                if (pk1.equals(winnerKey)) {
                    p1Wins++;
                } else if (pk2.equals(winnerKey)) {
                    p2Wins++;
                }
            } catch (Exception e) {
                // Skip malformed dates
            }
        }

        // Return in the original player key order
        if (playerKey1.compareTo(playerKey2) < 0) {
            return new int[]{p1Wins, p2Wins};
        } else {
            return new int[]{p2Wins, p1Wins};
        }
    }

    /**
     * Get H2H matches between two players, optionally filtered by date.
     * 
     * @param playerKey1 First player key
     * @param playerKey2 Second player key
     * @param beforeDate If not null, only return matches before this date
     * @return List of match documents from the H2H data
     */
    public List<Document> getH2HMatches(String playerKey1, String playerKey2, LocalDate beforeDate) {
        H2HDocument h2h = fetchH2H(playerKey1, playerKey2, false);
        if (h2h == null || h2h.getRaw() == null) {
            return new ArrayList<>();
        }

        Object h2hMatchesObj = h2h.getRaw().get("H2H");
        if (!(h2hMatchesObj instanceof List)) {
            return new ArrayList<>();
        }

        @SuppressWarnings("unchecked")
        List<Document> h2hMatches = (List<Document>) h2hMatchesObj;
        
        if (beforeDate == null) {
            return new ArrayList<>(h2hMatches);
        }

        // Filter by date
        List<Document> filtered = new ArrayList<>();
        for (Document match : h2hMatches) {
            String dateStr = match.getString("event_date");
            if (dateStr == null) continue;
            
            try {
                LocalDate matchDate = LocalDate.parse(dateStr.substring(0, 10));
                if (matchDate.isBefore(beforeDate)) {
                    filtered.add(match);
                }
            } catch (Exception e) {
                // Skip malformed dates
            }
        }
        
        return filtered;
    }

    // ============ FIXTURE MANAGEMENT ============

    /**
     * Reset results for all fixtures from a given date onwards.
     * This clears winner, score, and sets status back to "Not Started".
     * Useful for re-testing predictions against matches that have already occurred.
     * 
     * @param fromDate The start date (inclusive) - all fixtures from this date onwards will be reset
     * @return The number of fixtures that were reset
     */
    public int resetResultsFromDate(LocalDate fromDate) {
        log.info("Resetting results for fixtures from {} onwards", fromDate);
        
        List<FixtureDocument> fixtures = fixtureRepository.findByEventDateGreaterThanEqual(fromDate);
        
        int resetCount = 0;
        for (FixtureDocument fixture : fixtures) {
            // Only reset if the fixture has some result data
            if (fixture.getWinner() != null || 
                "Finished".equalsIgnoreCase(fixture.getStatus()) ||
                "Retired".equalsIgnoreCase(fixture.getStatus()) ||
                "Walk Over".equalsIgnoreCase(fixture.getStatus()) ||
                "Cancelled".equalsIgnoreCase(fixture.getStatus())) {
                
                fixture.setStatus("Not Started");
                fixture.setWinner(null);
                fixture.setScore(null);
                resetCount++;
            }
        }
        
        if (resetCount > 0) {
            fixtureRepository.saveAll(fixtures);
        }
        
        log.info("Reset {} fixtures from {} onwards", resetCount, fromDate);
        return resetCount;
    }
}
