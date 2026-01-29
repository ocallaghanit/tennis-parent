package com.tennis.prediction.controller;

import com.tennis.prediction.model.readonly.*;
import com.tennis.prediction.repository.readonly.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/data")
@Tag(name = "Data", description = "Read-only access to adapter data")
public class DataController {

    private final FixtureReadRepository fixtureRepository;
    private final PlayerReadRepository playerRepository;
    private final TournamentReadRepository tournamentRepository;
    private final H2HReadRepository h2hRepository;
    private final OddsReadRepository oddsRepository;

    public DataController(
            FixtureReadRepository fixtureRepository,
            PlayerReadRepository playerRepository,
            TournamentReadRepository tournamentRepository,
            H2HReadRepository h2hRepository,
            OddsReadRepository oddsRepository
    ) {
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.tournamentRepository = tournamentRepository;
        this.h2hRepository = h2hRepository;
        this.oddsRepository = oddsRepository;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get data statistics", description = "Overview of available data")
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("fixtures", fixtureRepository.count());
        stats.put("players", playerRepository.count());
        stats.put("tournaments", tournamentRepository.count());
        stats.put("h2h", h2hRepository.count());
        stats.put("odds", oddsRepository.count());
        return stats;
    }

    // ============ FIXTURES ============

    @GetMapping("/fixtures")
    @Operation(summary = "Get fixtures by date range")
    public List<FixtureDocument> getFixtures(
            @RequestParam String dateStart,
            @RequestParam String dateStop
    ) {
        return fixtureRepository.findByDateRange(
                LocalDate.parse(dateStart),
                LocalDate.parse(dateStop)
        );
    }

    @GetMapping("/fixtures/{eventKey}")
    @Operation(summary = "Get fixture by event key")
    public Optional<FixtureDocument> getFixture(@PathVariable String eventKey) {
        return fixtureRepository.findByEventKey(eventKey);
    }

    @GetMapping("/fixtures/player/{playerKey}")
    @Operation(summary = "Get fixtures for a player")
    public List<FixtureDocument> getPlayerFixtures(@PathVariable String playerKey) {
        return fixtureRepository.findByPlayerKey(playerKey);
    }

    // ============ PLAYERS ============

    @GetMapping("/players/{playerKey}")
    @Operation(summary = "Get player by key")
    public Optional<PlayerDocument> getPlayer(@PathVariable String playerKey) {
        return playerRepository.findByPlayerKey(playerKey);
    }

    @GetMapping("/players/ranked")
    @Operation(summary = "Get ranked players")
    public List<PlayerDocument> getRankedPlayers() {
        return playerRepository.findByCurrentRankNotNullOrderByCurrentRankAsc();
    }

    // ============ TOURNAMENTS ============

    @GetMapping("/tournaments/{tournamentKey}")
    @Operation(summary = "Get tournament by key")
    public Optional<TournamentDocument> getTournament(@PathVariable String tournamentKey) {
        return tournamentRepository.findByTournamentKey(tournamentKey);
    }

    @GetMapping("/tournaments/surface/{surface}")
    @Operation(summary = "Get tournaments by surface")
    public List<TournamentDocument> getTournamentsBySurface(@PathVariable String surface) {
        return tournamentRepository.findBySurface(surface);
    }

    // ============ HEAD-TO-HEAD ============

    @GetMapping("/h2h/{player1}/{player2}")
    @Operation(summary = "Get head-to-head between two players")
    public Optional<H2HDocument> getH2H(
            @PathVariable String player1,
            @PathVariable String player2
    ) {
        return h2hRepository.findH2H(player1, player2);
    }

    // ============ ODDS ============

    @GetMapping("/odds/{matchKey}")
    @Operation(summary = "Get odds for a match")
    public Optional<OddsDocument> getOdds(@PathVariable String matchKey) {
        return oddsRepository.findByMatchKey(matchKey);
    }
}

