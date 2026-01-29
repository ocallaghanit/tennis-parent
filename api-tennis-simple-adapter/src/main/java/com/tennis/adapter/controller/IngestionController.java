package com.tennis.adapter.controller;

import com.tennis.adapter.model.IngestionResult;
import com.tennis.adapter.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints to trigger data ingestion from API Tennis.
 */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestion", description = "Endpoints to fetch data from API Tennis and store in MongoDB")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/events")
    @Operation(summary = "Ingest event types", description = "Fetch all event types (ATP Singles, WTA Singles, etc.) from API Tennis")
    public ResponseEntity<Map<String, Object>> ingestEvents() {
        IngestionResult result = ingestionService.ingestEvents();
        return buildResponse(result);
    }

    @PostMapping("/tournaments")
    @Operation(summary = "Ingest tournaments", description = "Fetch tournaments, optionally filtered by event type (265=ATP Singles, 281=WTA Singles)")
    public ResponseEntity<Map<String, Object>> ingestTournaments(
            @Parameter(description = "Event type key (e.g., 265 for ATP Singles, 281 for WTA Singles)")
            @RequestParam(required = false) String eventTypeKey
    ) {
        IngestionResult result = ingestionService.ingestTournaments(eventTypeKey);
        return buildResponse(result);
    }

    @PostMapping("/fixtures")
    @Operation(summary = "Ingest fixtures by date range", 
               description = "Fetch all matches within a date range. Use batched=true for automatic 7-day chunking (recommended for ranges > 7 days)")
    public ResponseEntity<Map<String, Object>> ingestFixtures(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop,
            @Parameter(description = "Auto-split into 7-day batches for reliability", example = "true")
            @RequestParam(defaultValue = "false") boolean batched
    ) {
        IngestionResult result = batched 
                ? ingestionService.ingestFixturesBatched(dateStart, dateStop)
                : ingestionService.ingestFixtures(dateStart, dateStop);
        return buildResponse(result);
    }

    @PostMapping("/fixtures/tournament/{tournamentKey}")
    @Operation(summary = "Ingest fixtures by tournament", description = "Fetch all matches for a specific tournament")
    public ResponseEntity<Map<String, Object>> ingestFixturesByTournament(
            @Parameter(description = "Tournament key (e.g., 2553 for Wimbledon)")
            @PathVariable String tournamentKey
    ) {
        IngestionResult result = ingestionService.ingestFixturesByTournament(tournamentKey);
        return buildResponse(result);
    }

    @PostMapping("/players/{playerKey}")
    @Operation(summary = "Ingest a player", description = "Fetch and store a single player's profile")
    public ResponseEntity<Map<String, Object>> ingestPlayer(
            @Parameter(description = "Player key from API Tennis")
            @PathVariable String playerKey,
            @Parameter(description = "Force re-fetch even if recently updated")
            @RequestParam(defaultValue = "false") boolean force
    ) {
        IngestionResult result = ingestionService.ingestPlayer(playerKey, force);
        return buildResponse(result);
    }

    @PostMapping("/players/sync")
    @Operation(summary = "Sync players from fixtures", 
               description = "Extract unique player keys from stored fixtures and fetch missing/stale profiles. " +
                       "Rate-limited (200ms between calls) to avoid API throttling. " +
                       "Skips players fetched within the last 14 days unless force=true.")
    public ResponseEntity<Map<String, Object>> syncPlayersFromFixtures(
            @Parameter(description = "Force re-fetch all players (ignores TTL)")
            @RequestParam(defaultValue = "false") boolean force
    ) {
        IngestionResult result = ingestionService.syncPlayersFromFixtures(force);
        return buildResponse(result);
    }

    @PostMapping("/odds")
    @Operation(summary = "Ingest odds by date range", 
               description = "Fetch betting odds for all matches in a date range. Use batched=true for automatic 7-day chunking (recommended for ranges > 7 days)")
    public ResponseEntity<Map<String, Object>> ingestOdds(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop,
            @Parameter(description = "Auto-split into 7-day batches for reliability", example = "true")
            @RequestParam(defaultValue = "false") boolean batched
    ) {
        IngestionResult result = batched 
                ? ingestionService.ingestOddsBatched(dateStart, dateStop)
                : ingestionService.ingestOdds(dateStart, dateStop);
        return buildResponse(result);
    }

    @PostMapping("/odds/{matchKey}")
    @Operation(summary = "Ingest odds for a match", description = "Fetch and store odds for a specific match")
    public ResponseEntity<Map<String, Object>> ingestOddsForMatch(
            @Parameter(description = "Match/event key")
            @PathVariable String matchKey
    ) {
        IngestionResult result = ingestionService.ingestOddsForMatch(matchKey);
        return buildResponse(result);
    }

    @DeleteMapping("/cleanup/non-singles")
    @Operation(summary = "Cleanup non-singles data", 
               description = "Remove all non-Men's Singles fixtures (doubles, WTA, etc.) from the database. " +
                       "Also removes associated odds. This is a destructive operation!")
    public ResponseEntity<Map<String, Object>> cleanupNonSingles() {
        IngestionResult result = ingestionService.cleanupNonSinglesData();
        return buildResponse(result);
    }

    @PostMapping("/catalog")
    @Operation(summary = "Full catalog refresh", description = "Ingest all events + all tournaments (API returns full catalog regardless of filter)")
    public ResponseEntity<Map<String, Object>> ingestCatalog() {
        IngestionResult eventsResult = ingestionService.ingestEvents();
        // Note: API Tennis returns ALL tournaments regardless of event_type_id filter,
        // so we only need to call this once (no filter)
        IngestionResult tournamentsResult = ingestionService.ingestTournaments(null);
        
        boolean allSuccess = eventsResult.isSuccess() && tournamentsResult.isSuccess();
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", allSuccess);
        
        if (allSuccess) {
            response.put("message", String.format("Catalog ingested: %d events, %d tournaments", 
                    eventsResult.getCount(), tournamentsResult.getCount()));
        } else {
            StringBuilder errors = new StringBuilder();
            if (!eventsResult.isSuccess()) errors.append("Events: ").append(eventsResult.getMessage()).append("; ");
            if (!tournamentsResult.isSuccess()) errors.append("Tournaments: ").append(tournamentsResult.getMessage());
            response.put("message", "Partial failure: " + errors.toString().trim());
        }
        
        response.put("events", Map.of(
                "success", eventsResult.isSuccess(),
                "count", eventsResult.getCount(),
                "message", eventsResult.getMessage()
        ));
        response.put("tournaments", Map.of(
                "success", tournamentsResult.isSuccess(),
                "count", tournamentsResult.getCount(),
                "message", tournamentsResult.getMessage()
        ));
        
        return ResponseEntity.ok(response);
    }

    /**
     * Build a consistent response from an IngestionResult
     */
    private ResponseEntity<Map<String, Object>> buildResponse(IngestionResult result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("count", result.getCount());
        
        if (!result.isSuccess() && result.getErrorType() != null) {
            response.put("errorType", result.getErrorType());
        }
        
        // Always return 200 OK - the success flag indicates business-level success/failure
        // This ensures Swagger UI displays the response properly
        return ResponseEntity.ok(response);
    }
}
