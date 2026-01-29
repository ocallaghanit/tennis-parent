package com.tennis.adapter.controller;

import com.tennis.adapter.dto.*;
import com.tennis.adapter.exception.ResourceNotFoundException;
import com.tennis.adapter.model.*;
import com.tennis.adapter.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * REST endpoints to query stored data.
 * Provides paginated, filterable access to tennis data.
 */
@RestController
@RequestMapping("/api/data")
@Tag(name = "Data", description = "Query stored tennis data from MongoDB")
public class DataController {

    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerRepository playerRepository;
    private final OddsRepository oddsRepository;

    public DataController(
            EventRepository eventRepository,
            TournamentRepository tournamentRepository,
            FixtureRepository fixtureRepository,
            PlayerRepository playerRepository,
            OddsRepository oddsRepository
    ) {
        this.eventRepository = eventRepository;
        this.tournamentRepository = tournamentRepository;
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.oddsRepository = oddsRepository;
    }

    // =============== EVENTS ===============

    @GetMapping("/events")
    @Operation(summary = "List all events", description = "Get all stored event types (cached for 1 hour)")
    public ResponseEntity<List<EventDocument>> getAllEvents() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(eventRepository.findAll());
    }

    @GetMapping("/events/{eventKey}")
    @Operation(summary = "Get event by key", description = "Get a specific event type")
    public ResponseEntity<EventDocument> getEvent(
            @Parameter(description = "Event key (e.g., 265)")
            @PathVariable String eventKey
    ) {
        return eventRepository.findByEventKey(eventKey)
                .map(event -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                        .body(event))
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventKey));
    }

    // =============== TOURNAMENTS ===============

    @GetMapping("/tournaments")
    @Operation(summary = "List tournaments", description = "Get all tournaments, optionally filtered by event type (cached for 1 hour)")
    public ResponseEntity<List<TournamentResponse>> getAllTournaments(
            @Parameter(description = "Filter by event type key (265=ATP Singles, 281=Challenger Singles)")
            @RequestParam(required = false) String eventTypeKey
    ) {
        List<TournamentDocument> tournaments;
        if (eventTypeKey != null && !eventTypeKey.isBlank()) {
            tournaments = tournamentRepository.findByEventTypeKey(eventTypeKey);
        } else {
            tournaments = tournamentRepository.findAll();
        }
        
        List<TournamentResponse> response = tournaments.stream()
                .map(TournamentResponse::from)
                .toList();
        
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(response);
    }

    @GetMapping("/tournaments/{tournamentKey}")
    @Operation(summary = "Get tournament by key", description = "Get a specific tournament")
    public ResponseEntity<TournamentResponse> getTournament(
            @Parameter(description = "Tournament key (e.g., 1236 for Australian Open)")
            @PathVariable String tournamentKey
    ) {
        return tournamentRepository.findByTournamentKey(tournamentKey)
                .map(t -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                        .body(TournamentResponse.from(t)))
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", tournamentKey));
    }

    // =============== FIXTURES ===============

    @GetMapping("/fixtures")
    @Operation(summary = "List fixtures (paginated)", description = "Get fixtures with optional filters and pagination")
    public ResponseEntity<PageResponse<FixtureResponse>> getFixtures(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop,
            @Parameter(description = "Tournament key")
            @RequestParam(required = false) String tournamentKey,
            @Parameter(description = "Status filter (Finished, Not Started, Live, Cancelled)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "50")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Sort field", example = "eventDate")
            @RequestParam(defaultValue = "eventDate") String sortBy,
            @Parameter(description = "Sort direction (ASC/DESC)", example = "DESC")
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort); // Max 100 per page

        Page<FixtureDocument> fixtures;
        
        // Build query based on provided filters
        if (tournamentKey != null && !tournamentKey.isBlank()) {
            if (dateStart != null && dateStop != null) {
                if (status != null && !status.isBlank()) {
                    fixtures = fixtureRepository.findByTournamentKeyAndEventDateBetweenAndStatus(
                            tournamentKey, dateStart, dateStop, status, pageable);
                } else {
                    fixtures = fixtureRepository.findByTournamentKeyAndEventDateBetween(
                            tournamentKey, dateStart, dateStop, pageable);
                }
            } else if (status != null && !status.isBlank()) {
                fixtures = fixtureRepository.findByTournamentKeyAndStatus(tournamentKey, status, pageable);
            } else {
                fixtures = fixtureRepository.findByTournamentKey(tournamentKey, pageable);
            }
        } else if (dateStart != null && dateStop != null) {
            if (status != null && !status.isBlank()) {
                fixtures = fixtureRepository.findByEventDateBetweenAndStatus(dateStart, dateStop, status, pageable);
            } else {
                fixtures = fixtureRepository.findByEventDateBetween(dateStart, dateStop, pageable);
            }
        } else if (status != null && !status.isBlank()) {
            fixtures = fixtureRepository.findByStatus(status, pageable);
        } else {
            fixtures = fixtureRepository.findAll(pageable);
        }

        return ResponseEntity.ok(PageResponse.from(fixtures, FixtureResponse::from));
    }

    @GetMapping("/fixtures/{eventKey}")
    @Operation(summary = "Get fixture by key", description = "Get a specific fixture/match with optional raw data")
    public ResponseEntity<?> getFixture(
            @Parameter(description = "Event/match key")
            @PathVariable String eventKey,
            @Parameter(description = "Include raw API data")
            @RequestParam(defaultValue = "false") boolean includeRaw
    ) {
        FixtureDocument fixture = fixtureRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new ResourceNotFoundException("Fixture", eventKey));
        
        if (includeRaw) {
            return ResponseEntity.ok(fixture);
        }
        return ResponseEntity.ok(FixtureResponse.from(fixture));
    }

    @GetMapping("/fixtures/player/{playerKey}")
    @Operation(summary = "Get player's fixtures", description = "Get all matches for a specific player")
    public ResponseEntity<List<FixtureResponse>> getFixturesByPlayer(
            @Parameter(description = "Player key")
            @PathVariable String playerKey,
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop
    ) {
        List<FixtureDocument> fixtures;
        if (dateStart != null && dateStop != null) {
            fixtures = fixtureRepository.findByPlayerKeyAndDateRange(playerKey, dateStart, dateStop);
        } else {
            fixtures = fixtureRepository.findByPlayerKey(playerKey);
        }
        
        List<FixtureResponse> response = fixtures.stream()
                .map(FixtureResponse::from)
                .toList();
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fixtures/h2h")
    @Operation(summary = "Head-to-head", description = "Get all matches between two players with win/loss summary")
    public ResponseEntity<H2HResponse> getHeadToHead(
            @Parameter(description = "First player key", required = true)
            @RequestParam String player1Key,
            @Parameter(description = "Second player key", required = true)
            @RequestParam String player2Key
    ) {
        List<FixtureDocument> h2hMatches = fixtureRepository.findH2HMatches(player1Key, player2Key);
        
        // Get player names from first match or repository
        String player1Name = h2hMatches.stream()
                .filter(m -> player1Key.equals(m.getFirstPlayerKey()))
                .map(FixtureDocument::getFirstPlayerName)
                .findFirst()
                .or(() -> h2hMatches.stream()
                        .filter(m -> player1Key.equals(m.getSecondPlayerKey()))
                        .map(FixtureDocument::getSecondPlayerName)
                        .findFirst())
                .or(() -> playerRepository.findByPlayerKey(player1Key)
                        .map(PlayerDocument::getPlayerName))
                .orElse(player1Key);
        
        String player2Name = h2hMatches.stream()
                .filter(m -> player2Key.equals(m.getFirstPlayerKey()))
                .map(FixtureDocument::getFirstPlayerName)
                .findFirst()
                .or(() -> h2hMatches.stream()
                        .filter(m -> player2Key.equals(m.getSecondPlayerKey()))
                        .map(FixtureDocument::getSecondPlayerName)
                        .findFirst())
                .or(() -> playerRepository.findByPlayerKey(player2Key)
                        .map(PlayerDocument::getPlayerName))
                .orElse(player2Key);
        
        List<FixtureResponse> matches = h2hMatches.stream()
                .map(FixtureResponse::from)
                .toList();
        
        return ResponseEntity.ok(H2HResponse.create(player1Key, player1Name, player2Key, player2Name, matches));
    }

    @PostMapping("/fixtures/bulk")
    @Operation(summary = "Bulk fixture lookup", description = "Get multiple fixtures by their event keys")
    public ResponseEntity<List<FixtureResponse>> getFixturesBulk(
            @Parameter(description = "List of event keys")
            @RequestBody List<String> eventKeys
    ) {
        if (eventKeys == null || eventKeys.isEmpty()) {
            throw new IllegalArgumentException("Event keys list cannot be empty");
        }
        if (eventKeys.size() > 100) {
            throw new IllegalArgumentException("Maximum 100 event keys per request");
        }
        
        List<FixtureResponse> fixtures = fixtureRepository.findByEventKeyIn(eventKeys).stream()
                .map(FixtureResponse::from)
                .toList();
        
        return ResponseEntity.ok(fixtures);
    }

    // =============== PLAYERS ===============

    @GetMapping("/players")
    @Operation(summary = "List players (paginated)", description = "Get players with optional filters and pagination")
    public ResponseEntity<PageResponse<PlayerResponse>> getAllPlayers(
            @Parameter(description = "Filter by country")
            @RequestParam(required = false) String country,
            @Parameter(description = "Minimum rank")
            @RequestParam(required = false) Integer minRank,
            @Parameter(description = "Maximum rank")
            @RequestParam(required = false) Integer maxRank,
            @Parameter(description = "Page number (0-indexed)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size", example = "50")
            @RequestParam(defaultValue = "50") int size,
            @Parameter(description = "Sort field", example = "currentRank")
            @RequestParam(defaultValue = "currentRank") String sortBy,
            @Parameter(description = "Sort direction (ASC/DESC)", example = "ASC")
            @RequestParam(defaultValue = "ASC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<PlayerDocument> players;
        
        if (country != null && !country.isBlank()) {
            players = playerRepository.findByCountry(country, pageable);
        } else if (minRank != null && maxRank != null) {
            players = playerRepository.findByCurrentRankBetween(minRank, maxRank, pageable);
        } else {
            players = playerRepository.findAll(pageable);
        }

        return ResponseEntity.ok(PageResponse.from(players, PlayerResponse::from));
    }

    @GetMapping("/players/{playerKey}")
    @Operation(summary = "Get player by key", description = "Get a specific player's profile with optional raw data")
    public ResponseEntity<?> getPlayer(
            @Parameter(description = "Player key")
            @PathVariable String playerKey,
            @Parameter(description = "Include raw API data")
            @RequestParam(defaultValue = "false") boolean includeRaw
    ) {
        PlayerDocument player = playerRepository.findByPlayerKey(playerKey)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerKey));
        
        if (includeRaw) {
            return ResponseEntity.ok(player);
        }
        return ResponseEntity.ok(PlayerResponse.from(player));
    }

    @GetMapping("/players/search")
    @Operation(summary = "Search players", description = "Search players by name (case-insensitive)")
    public ResponseEntity<List<PlayerResponse>> searchPlayers(
            @Parameter(description = "Search query (name)", required = true)
            @RequestParam String query,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (query == null || query.length() < 2) {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }
        
        List<PlayerResponse> players = playerRepository.findByPlayerNameContaining(query).stream()
                .limit(Math.min(limit, 50))
                .map(PlayerResponse::from)
                .toList();
        
        return ResponseEntity.ok(players);
    }

    @GetMapping("/players/ranked")
    @Operation(summary = "Get ranked players", description = "Get players with ATP ranking (paginated)")
    public ResponseEntity<PageResponse<PlayerResponse>> getRankedPlayers(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("currentRank").ascending());
        Page<PlayerDocument> players = playerRepository.findByCurrentRankNotNull(pageable);
        return ResponseEntity.ok(PageResponse.from(players, PlayerResponse::from));
    }

    @PostMapping("/players/bulk")
    @Operation(summary = "Bulk player lookup", description = "Get multiple players by their keys")
    public ResponseEntity<List<PlayerResponse>> getPlayersBulk(
            @Parameter(description = "List of player keys")
            @RequestBody List<String> playerKeys
    ) {
        if (playerKeys == null || playerKeys.isEmpty()) {
            throw new IllegalArgumentException("Player keys list cannot be empty");
        }
        if (playerKeys.size() > 100) {
            throw new IllegalArgumentException("Maximum 100 player keys per request");
        }
        
        List<PlayerResponse> players = playerRepository.findByPlayerKeyIn(playerKeys).stream()
                .map(PlayerResponse::from)
                .toList();
        
        return ResponseEntity.ok(players);
    }

    // =============== ODDS ===============

    @GetMapping("/odds")
    @Operation(summary = "List odds", description = "Get stored odds with optional filters")
    public List<OddsDocument> getOdds(
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop,
            @Parameter(description = "Tournament key")
            @RequestParam(required = false) String tournamentKey
    ) {
        if (tournamentKey != null && !tournamentKey.isBlank()) {
            return oddsRepository.findByTournamentKey(tournamentKey);
        }
        if (dateStart != null && dateStop != null) {
            return oddsRepository.findByEventDateBetween(dateStart, dateStop);
        }
        return oddsRepository.findAll();
    }

    @GetMapping("/odds/{matchKey}")
    @Operation(summary = "Get odds by match", description = "Get odds for a specific match")
    public ResponseEntity<OddsDocument> getOddsByMatch(
            @Parameter(description = "Match/event key")
            @PathVariable String matchKey
    ) {
        return oddsRepository.findByMatchKey(matchKey)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("Odds for match", matchKey));
    }

    // =============== SYNC / CHANGES ===============

    @GetMapping("/changes/fixtures")
    @Operation(summary = "Get fixture changes", description = "Get fixtures updated since a timestamp (for incremental sync)")
    public ResponseEntity<List<FixtureResponse>> getFixtureChanges(
            @Parameter(description = "Timestamp (ISO-8601)", required = true, example = "2026-01-26T10:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        List<FixtureResponse> changes = fixtureRepository.findByUpdatedAtAfter(since).stream()
                .map(FixtureResponse::from)
                .toList();
        
        return ResponseEntity.ok(changes);
    }

    @GetMapping("/changes/players")
    @Operation(summary = "Get player changes", description = "Get players updated since a timestamp (for incremental sync)")
    public ResponseEntity<List<PlayerResponse>> getPlayerChanges(
            @Parameter(description = "Timestamp (ISO-8601)", required = true, example = "2026-01-26T10:00:00Z")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since
    ) {
        List<PlayerResponse> changes = playerRepository.findByUpdatedAtAfter(since).stream()
                .map(PlayerResponse::from)
                .toList();
        
        return ResponseEntity.ok(changes);
    }

    // =============== STATS ===============

    @GetMapping("/stats")
    @Operation(summary = "Get statistics", description = "Get document counts for all collections")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(Map.of(
                        "events", eventRepository.count(),
                        "tournaments", tournamentRepository.count(),
                        "fixtures", fixtureRepository.count(),
                        "players", playerRepository.count(),
                        "odds", oddsRepository.count()
                ));
    }
}
