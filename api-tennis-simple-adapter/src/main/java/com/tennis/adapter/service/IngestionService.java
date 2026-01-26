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

/**
 * Service for ingesting data from API Tennis and storing in MongoDB.
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

    private final ApiTennisClient apiClient;
    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerRepository playerRepository;
    private final OddsRepository oddsRepository;

    public IngestionService(
            ApiTennisClient apiClient,
            EventRepository eventRepository,
            TournamentRepository tournamentRepository,
            FixtureRepository fixtureRepository,
            PlayerRepository playerRepository,
            OddsRepository oddsRepository
    ) {
        this.apiClient = apiClient;
        this.eventRepository = eventRepository;
        this.tournamentRepository = tournamentRepository;
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.oddsRepository = oddsRepository;
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
                doc.setSurface(getTextOrNull(node, "surface"));
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
        List<String> playerKeys = new ArrayList<>();

        for (JsonNode node : result) {
            String eventKey = getTextOrNull(node, "event_key");
            if (eventKey == null) continue;

            FixtureDocument doc = fixtureRepository.findByEventKey(eventKey)
                    .orElse(new FixtureDocument());
            
            doc.setEventKey(eventKey);
            doc.setTournamentKey(getTextOrNull(node, "tournament_key"));
            
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
            doc.setWinner(getTextOrNull(node, "event_winner"));
            doc.setScore(getTextOrNull(node, "event_final_result"));
            doc.setRaw(Document.parse(node.toString()));
            doc.setUpdatedAt(Instant.now());
            
            if (doc.getId() == null) {
                doc.setId(eventKey);
            }
            
            fixtureRepository.save(doc);
            count++;

            // Collect player keys for potential ingestion
            if (doc.getFirstPlayerKey() != null) playerKeys.add(doc.getFirstPlayerKey());
            if (doc.getSecondPlayerKey() != null) playerKeys.add(doc.getSecondPlayerKey());
        }

        log.info("Ingested {} fixtures", count);
        return IngestionResult.success(count, "Ingested " + count + " fixtures");
    }

    /**
     * Ingest a single player
     */
    public IngestionResult ingestPlayer(String playerKey) {
        log.info("Ingesting player={}", playerKey);
        
        try {
            // Check if already exists and was fetched recently
            var existing = playerRepository.findByPlayerKey(playerKey);
            if (existing.isPresent()) {
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
            
            String rankStr = getTextOrNull(node, "player_rank");
            if (rankStr != null && !rankStr.isEmpty()) {
                try {
                    doc.setCurrentRank(Integer.parseInt(rankStr));
                } catch (NumberFormatException ignored) {}
            }
            
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
        log.info("Starting player sync from fixtures...");
        
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
            
            // Step 2: Filter out players that are already up-to-date
            Instant ttlThreshold = Instant.now().minus(PLAYER_TTL_DAYS, ChronoUnit.DAYS);
            List<PlayerDocument> existingPlayers = playerRepository.findAll();
            Set<String> upToDateKeys = new HashSet<>();
            
            for (PlayerDocument player : existingPlayers) {
                if (player.getFetchedAt() != null && player.getFetchedAt().isAfter(ttlThreshold)) {
                    upToDateKeys.add(player.getPlayerKey());
                }
            }
            
            // Remove up-to-date players from fetch list
            playerKeys.removeAll(upToDateKeys);
            
            if (playerKeys.isEmpty()) {
                log.info("All {} players are up-to-date (fetched within {} days)", 
                        upToDateKeys.size(), PLAYER_TTL_DAYS);
                return IngestionResult.success(0, 
                        String.format("All %d players already up-to-date", upToDateKeys.size()));
            }
            
            log.info("Need to fetch {} players ({} already up-to-date)", 
                    playerKeys.size(), upToDateKeys.size());
            
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
                            
                            String rankStr = getTextOrNull(node, "player_rank");
                            if (rankStr != null && !rankStr.isEmpty()) {
                                try {
                                    doc.setCurrentRank(Integer.parseInt(rankStr));
                                } catch (NumberFormatException ignored) {}
                            }
                            
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
                    total, fetched, failed, upToDateKeys.size());
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
     * Handles both array format [{match_key: "123", ...}] and object format {"123": {...}}
     */
    public IngestionResult ingestOdds(LocalDate dateStart, LocalDate dateStop) {
        log.info("Ingesting odds from {} to {}", dateStart, dateStop);
        
        try {
            JsonNode response = apiClient.getOddsByDateRange(
                    dateStart.toString(),
                    dateStop.toString()
            );
            
            if (!isSuccessful(response)) {
                log.error("Failed to fetch odds - API returned unsuccessful response");
                return IngestionResult.apiError("API Tennis returned unsuccessful response for odds");
            }

            JsonNode result = response.get("result");
            if (result == null) {
                log.warn("No odds result in response");
                return IngestionResult.success(0, "No odds found in API response");
            }

            int count = 0;
            
            // Handle array format: [{match_key: "123", odds: {...}}, ...]
            if (result.isArray()) {
                log.debug("Processing odds in array format");
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
                return IngestionResult.success(0, "No odds found - unexpected response format");
            }

            log.info("Ingested {} odds records", count);
            return IngestionResult.success(count, "Ingested " + count + " odds records");
            
        } catch (WebClientResponseException e) {
            log.error("API Tennis HTTP error while fetching odds: {} {}", e.getStatusCode(), e.getMessage());
            String hint = extractErrorHint(e);
            if (e.getStatusCode().value() == 500) {
                hint += " (try a smaller date range or wait and retry)";
            }
            return IngestionResult.apiError(e.getStatusCode().value(), "Failed to fetch odds - " + hint);
        } catch (Exception e) {
            log.error("Error ingesting odds: {}", e.getMessage(), e);
            return IngestionResult.failure("Error ingesting odds: " + e.getMessage());
        }
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

    // Helper methods
    
    private boolean isSuccessful(JsonNode response) {
        return response != null && 
               response.has("success") && 
               response.get("success").asInt() == 1;
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
}
