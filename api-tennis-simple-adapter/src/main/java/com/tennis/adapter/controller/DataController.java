package com.tennis.adapter.controller;

import com.tennis.adapter.model.*;
import com.tennis.adapter.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints to query stored data.
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
    @Operation(summary = "List all events", description = "Get all stored event types")
    public List<EventDocument> getAllEvents() {
        return eventRepository.findAll();
    }

    @GetMapping("/events/{eventKey}")
    @Operation(summary = "Get event by key", description = "Get a specific event type")
    public ResponseEntity<EventDocument> getEvent(
            @Parameter(description = "Event key (e.g., 265)")
            @PathVariable String eventKey
    ) {
        return eventRepository.findByEventKey(eventKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =============== TOURNAMENTS ===============

    @GetMapping("/tournaments")
    @Operation(summary = "List tournaments", description = "Get all tournaments, optionally filtered by event type")
    public List<TournamentDocument> getAllTournaments(
            @Parameter(description = "Filter by event type key (265=ATP, 281=WTA)")
            @RequestParam(required = false) String eventTypeKey
    ) {
        if (eventTypeKey != null && !eventTypeKey.isBlank()) {
            return tournamentRepository.findByEventTypeKey(eventTypeKey);
        }
        return tournamentRepository.findAll();
    }

    @GetMapping("/tournaments/{tournamentKey}")
    @Operation(summary = "Get tournament by key", description = "Get a specific tournament")
    public ResponseEntity<TournamentDocument> getTournament(
            @Parameter(description = "Tournament key (e.g., 2553 for Wimbledon)")
            @PathVariable String tournamentKey
    ) {
        return tournamentRepository.findByTournamentKey(tournamentKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =============== FIXTURES ===============

    @GetMapping("/fixtures")
    @Operation(summary = "List fixtures", description = "Get fixtures with optional filters")
    public List<FixtureDocument> getFixtures(
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop,
            @Parameter(description = "Tournament key")
            @RequestParam(required = false) String tournamentKey
    ) {
        if (tournamentKey != null && !tournamentKey.isBlank()) {
            if (dateStart != null && dateStop != null) {
                return fixtureRepository.findByTournamentKeyAndEventDateBetween(tournamentKey, dateStart, dateStop);
            }
            return fixtureRepository.findByTournamentKey(tournamentKey);
        }
        if (dateStart != null && dateStop != null) {
            return fixtureRepository.findByEventDateBetween(dateStart, dateStop);
        }
        return fixtureRepository.findAll();
    }

    @GetMapping("/fixtures/{eventKey}")
    @Operation(summary = "Get fixture by key", description = "Get a specific fixture/match")
    public ResponseEntity<FixtureDocument> getFixture(
            @Parameter(description = "Event/match key")
            @PathVariable String eventKey
    ) {
        return fixtureRepository.findByEventKey(eventKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fixtures/player/{playerKey}")
    @Operation(summary = "Get player's fixtures", description = "Get all matches for a specific player")
    public List<FixtureDocument> getFixturesByPlayer(
            @Parameter(description = "Player key")
            @PathVariable String playerKey,
            @Parameter(description = "Start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStart,
            @Parameter(description = "End date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateStop
    ) {
        if (dateStart != null && dateStop != null) {
            return fixtureRepository.findByPlayerKeyAndDateRange(playerKey, dateStart, dateStop);
        }
        return fixtureRepository.findByPlayerKey(playerKey);
    }

    // =============== PLAYERS ===============

    @GetMapping("/players")
    @Operation(summary = "List all players", description = "Get all stored player profiles")
    public List<PlayerDocument> getAllPlayers() {
        return playerRepository.findAll();
    }

    @GetMapping("/players/{playerKey}")
    @Operation(summary = "Get player by key", description = "Get a specific player's profile")
    public ResponseEntity<PlayerDocument> getPlayer(
            @Parameter(description = "Player key")
            @PathVariable String playerKey
    ) {
        return playerRepository.findByPlayerKey(playerKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
                .orElse(ResponseEntity.notFound().build());
    }

    // =============== STATS ===============

    @GetMapping("/stats")
    @Operation(summary = "Get statistics", description = "Get document counts for all collections")
    public Map<String, Object> getStats() {
        return Map.of(
                "events", eventRepository.count(),
                "tournaments", tournamentRepository.count(),
                "fixtures", fixtureRepository.count(),
                "players", playerRepository.count(),
                "odds", oddsRepository.count()
        );
    }
}
