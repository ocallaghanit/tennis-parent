package com.tennis.adapter.controller;

import com.tennis.adapter.model.*;
import com.tennis.adapter.repository.*;
import com.tennis.adapter.service.IngestionService;
import com.tennis.adapter.service.PredictionVerificationService;
import com.tennis.adapter.service.PredictionVerificationService.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UI Controller for the Tennis Data Adapter dashboard.
 */
@Controller
@RequestMapping("/ui")
public class UiController {

    /**
     * Map of well-known tournament names (or keywords) to their countries.
     * Used when the API doesn't provide country data.
     */
    private static final Map<String, String> TOURNAMENT_COUNTRIES = Map.ofEntries(
            // Grand Slams
            Map.entry("Australian Open", "Australia"),
            Map.entry("Roland Garros", "France"),
            Map.entry("French Open", "France"),
            Map.entry("Wimbledon", "United Kingdom"),
            Map.entry("US Open", "United States"),
            // ATP 1000s
            Map.entry("Indian Wells", "United States"),
            Map.entry("Miami", "United States"),
            Map.entry("Monte Carlo", "Monaco"),
            Map.entry("Madrid", "Spain"),
            Map.entry("Rome", "Italy"),
            Map.entry("Canadian", "Canada"),
            Map.entry("Cincinnati", "United States"),
            Map.entry("Shanghai", "China"),
            Map.entry("Paris", "France"),
            // Other common tournaments
            Map.entry("Brisbane", "Australia"),
            Map.entry("Adelaide", "Australia"),
            Map.entry("Auckland", "New Zealand"),
            Map.entry("Buenos Aires", "Argentina"),
            Map.entry("Rotterdam", "Netherlands"),
            Map.entry("Dubai", "UAE"),
            Map.entry("Acapulco", "Mexico"),
            Map.entry("Barcelona", "Spain"),
            Map.entry("Munich", "Germany"),
            Map.entry("Stuttgart", "Germany"),
            Map.entry("Hamburg", "Germany"),
            Map.entry("Halle", "Germany"),
            Map.entry("Queen", "United Kingdom"),
            Map.entry("Eastbourne", "United Kingdom"),
            Map.entry("Atlanta", "United States"),
            Map.entry("Washington", "United States"),
            Map.entry("Winston-Salem", "United States"),
            Map.entry("Tokyo", "Japan"),
            Map.entry("Beijing", "China"),
            Map.entry("Stockholm", "Sweden"),
            Map.entry("Vienna", "Austria"),
            Map.entry("Basel", "Switzerland")
    );

    private final EventRepository eventRepository;
    private final TournamentRepository tournamentRepository;
    private final FixtureRepository fixtureRepository;
    private final PlayerRepository playerRepository;
    private final OddsRepository oddsRepository;
    private final IngestionService ingestionService;
    private final PredictionVerificationService verificationService;

    public UiController(
            EventRepository eventRepository,
            TournamentRepository tournamentRepository,
            FixtureRepository fixtureRepository,
            PlayerRepository playerRepository,
            OddsRepository oddsRepository,
            IngestionService ingestionService,
            PredictionVerificationService verificationService
    ) {
        this.eventRepository = eventRepository;
        this.tournamentRepository = tournamentRepository;
        this.fixtureRepository = fixtureRepository;
        this.playerRepository = playerRepository;
        this.oddsRepository = oddsRepository;
        this.ingestionService = ingestionService;
        this.verificationService = verificationService;
    }

    /**
     * Main dashboard showing overview of all data
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(7);
        LocalDate weekAhead = today.plusDays(7);

        // Stats
        model.addAttribute("eventCount", eventRepository.count());
        model.addAttribute("tournamentCount", tournamentRepository.count());
        model.addAttribute("fixtureCount", fixtureRepository.count());
        model.addAttribute("playerCount", playerRepository.count());
        model.addAttribute("oddsCount", oddsRepository.count());

        // Today's matches
        List<FixtureDocument> todayMatches = fixtureRepository.findByEventDateBetween(today, today);
        model.addAttribute("todayMatches", todayMatches);
        model.addAttribute("todayMatchCount", todayMatches.size());

        // Upcoming matches (next 7 days, excluding today)
        List<FixtureDocument> upcomingMatches = fixtureRepository.findByEventDateBetween(today.plusDays(1), weekAhead);
        // Group by date
        Map<LocalDate, List<FixtureDocument>> upcomingByDate = upcomingMatches.stream()
                .filter(f -> f.getEventDate() != null)
                .collect(Collectors.groupingBy(FixtureDocument::getEventDate, TreeMap::new, Collectors.toList()));
        model.addAttribute("upcomingByDate", upcomingByDate);
        model.addAttribute("upcomingMatchCount", upcomingMatches.size());

        // Recent results (last 7 days, excluding today)
        List<FixtureDocument> recentMatches = fixtureRepository.findByEventDateBetween(weekAgo, today.minusDays(1));
        // Filter to only finished matches and sort by date desc
        List<FixtureDocument> recentResults = recentMatches.stream()
                .filter(f -> "Finished".equalsIgnoreCase(f.getStatus()))
                .sorted((a, b) -> {
                    if (a.getEventDate() == null) return 1;
                    if (b.getEventDate() == null) return -1;
                    return b.getEventDate().compareTo(a.getEventDate());
                })
                .limit(50)
                .collect(Collectors.toList());
        model.addAttribute("recentResults", recentResults);
        model.addAttribute("recentResultCount", recentResults.size());

        // Current date for display
        model.addAttribute("currentDate", today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));

        return "dashboard";
    }

    /**
     * Fixtures browser with filtering.
     * Defaults to today's matches.
     */
    @GetMapping("/fixtures")
    public String fixtures(
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateStop,
            @RequestParam(required = false) String tournamentKey,
            @RequestParam(required = false) String status,
            Model model
    ) {
        // Default to today's matches
        LocalDate today = LocalDate.now();
        LocalDate start = dateStart != null ? LocalDate.parse(dateStart) : today;
        LocalDate stop = dateStop != null ? LocalDate.parse(dateStop) : today;

        List<FixtureDocument> fixtures;
        if (tournamentKey != null && !tournamentKey.isBlank()) {
            fixtures = fixtureRepository.findByTournamentKeyAndEventDateBetween(tournamentKey, start, stop);
        } else {
            fixtures = fixtureRepository.findByEventDateBetween(start, stop);
        }

        // Filter by status if provided
        if (status != null && !status.isBlank()) {
            final String statusFilter = status.trim();
            fixtures = fixtures.stream()
                    .filter(f -> {
                        String fixtureStatus = f.getStatus();
                        // "Not Started" should also match null/empty status (future matches)
                        if ("Not Started".equalsIgnoreCase(statusFilter)) {
                            return fixtureStatus == null 
                                || fixtureStatus.isBlank() 
                                || "Not Started".equalsIgnoreCase(fixtureStatus);
                        }
                        // For other statuses, do exact match
                        return statusFilter.equalsIgnoreCase(fixtureStatus);
                    })
                    .collect(Collectors.toList());
        }

        // Sort by date then time
        fixtures.sort((a, b) -> {
            if (a.getEventDate() == null) return 1;
            if (b.getEventDate() == null) return -1;
            int dateCompare = a.getEventDate().compareTo(b.getEventDate());
            if (dateCompare != 0) return dateCompare;
            // Could compare by time if stored
            return 0;
        });

        model.addAttribute("fixtures", fixtures);
        model.addAttribute("fixtureCount", fixtures.size());
        model.addAttribute("dateStart", start.toString());
        model.addAttribute("dateStop", stop.toString());
        model.addAttribute("tournamentKey", tournamentKey);
        model.addAttribute("status", status);

        // Get unique tournaments from fixtures in the date range (not all 9799!)
        List<FixtureDocument> allFixturesInRange = fixtureRepository.findByEventDateBetween(start, stop);
        Set<String> tournamentKeys = allFixturesInRange.stream()
                .map(FixtureDocument::getTournamentKey)
                .filter(k -> k != null)
                .collect(Collectors.toSet());
        
        // Fetch tournament details for the dropdown
        List<TournamentDocument> tournaments = tournamentKeys.stream()
                .map(key -> tournamentRepository.findByTournamentKey(key).orElse(null))
                .filter(t -> t != null)
                .sorted((a, b) -> {
                    String nameA = a.getTournamentName() != null ? a.getTournamentName() : "";
                    String nameB = b.getTournamentName() != null ? b.getTournamentName() : "";
                    return nameA.compareTo(nameB);
                })
                .collect(Collectors.toList());
        model.addAttribute("tournaments", tournaments);

        return "fixtures";
    }

    /**
     * Match details page
     */
    @GetMapping("/match/{eventKey}")
    public String matchDetails(@PathVariable String eventKey, Model model) {
        var fixtureOpt = fixtureRepository.findByEventKey(eventKey);
        
        if (fixtureOpt.isEmpty()) {
            model.addAttribute("error", "Match not found: " + eventKey);
            return "error";
        }
        
        FixtureDocument fixture = fixtureOpt.get();
        model.addAttribute("fixture", fixture);
        
        // Extract additional details from raw data
        if (fixture.getRaw() != null) {
            org.bson.Document raw = fixture.getRaw();
            
            // Match time
            if (raw.containsKey("event_time")) {
                model.addAttribute("matchTime", raw.get("event_time"));
            }
            
            // Tournament round (e.g., "1/8-finals", "Quarter-finals")
            if (raw.containsKey("tournament_round")) {
                String round = raw.getString("tournament_round");
                // Clean up round name - remove tournament prefix if present
                if (round != null && round.contains(" - ")) {
                    round = round.substring(round.lastIndexOf(" - ") + 3);
                }
                model.addAttribute("matchRound", round);
            }
            
            // Set scores - calculate from pointbypoint if available
            if (raw.containsKey("pointbypoint")) {
                Object pbp = raw.get("pointbypoint");
                if (pbp instanceof java.util.List) {
                    @SuppressWarnings("unchecked")
                    java.util.List<org.bson.Document> games = (java.util.List<org.bson.Document>) pbp;
                    java.util.List<String> setScores = calculateSetScores(games);
                    model.addAttribute("setScores", setScores);
                }
            }
            
            // Surface from tournament raw data (fallback)
            if (raw.containsKey("tournament_sourface")) {
                model.addAttribute("surface", raw.get("tournament_sourface"));
            }
            
            // Player images/logos
            if (raw.containsKey("event_first_player_logo")) {
                model.addAttribute("player1Image", raw.get("event_first_player_logo"));
            }
            if (raw.containsKey("event_second_player_logo")) {
                model.addAttribute("player2Image", raw.get("event_second_player_logo"));
            }
        }
        
        // Get tournament details
        String tournamentNameForCountryLookup = null;
        if (fixture.getTournamentKey() != null) {
            tournamentRepository.findByTournamentKey(fixture.getTournamentKey())
                    .ifPresent(t -> {
                        model.addAttribute("tournament", t);
                        model.addAttribute("tournamentName", t.getTournamentName());
                        // Surface from tournament document
                        if (t.getSurface() != null && !t.getSurface().isBlank()) {
                            model.addAttribute("surface", t.getSurface());
                        } else if (t.getRaw() != null && t.getRaw().containsKey("tournament_sourface")) {
                            model.addAttribute("surface", t.getRaw().get("tournament_sourface"));
                        }
                        // Country from tournament document
                        if (t.getCountry() != null && !t.getCountry().isBlank()) {
                            model.addAttribute("country", t.getCountry());
                        }
                    });
        }
        if (!model.containsAttribute("tournamentName")) {
            // Fallback to raw data if tournament not found
            if (fixture.getRaw() != null && fixture.getRaw().containsKey("tournament_name")) {
                model.addAttribute("tournamentName", fixture.getRaw().get("tournament_name"));
            } else {
                model.addAttribute("tournamentName", "Unknown Tournament");
            }
        }
        
        // Look up country from well-known tournaments if not set
        if (!model.containsAttribute("country")) {
            Object tNameObj = model.getAttribute("tournamentName");
            String tName = tNameObj != null ? tNameObj.toString() : "";
            String country = lookupTournamentCountry(tName);
            if (country != null) {
                model.addAttribute("country", country);
            }
        }
        
        // Get player profiles
        if (fixture.getFirstPlayerKey() != null) {
            playerRepository.findByPlayerKey(fixture.getFirstPlayerKey())
                    .ifPresent(p -> model.addAttribute("player1", p));
        }
        if (fixture.getSecondPlayerKey() != null) {
            playerRepository.findByPlayerKey(fixture.getSecondPlayerKey())
                    .ifPresent(p -> model.addAttribute("player2", p));
        }
        
        // Get odds if available
        oddsRepository.findByMatchKey(eventKey).ifPresent(o -> {
            model.addAttribute("odds", o);
            
            // Extract Home/Away odds for display
            if (o.getRaw() != null) {
                Object homeAwayObj = o.getRaw().get("Home/Away");
                if (homeAwayObj instanceof org.bson.Document) {
                    org.bson.Document homeAway = (org.bson.Document) homeAwayObj;
                    
                    String homeOdds = null;
                    String awayOdds = null;
                    
                    // Get Home odds (Player 1)
                    Object homeObj = homeAway.get("Home");
                    if (homeObj instanceof org.bson.Document) {
                        org.bson.Document home = (org.bson.Document) homeObj;
                        // Try bet365 first, then any available bookmaker
                        homeOdds = home.getString("bet365");
                        if (homeOdds == null) {
                            homeOdds = home.values().stream()
                                    .filter(v -> v instanceof String)
                                    .map(v -> (String) v)
                                    .findFirst().orElse(null);
                        }
                        model.addAttribute("homeOdds", homeOdds);
                    }
                    
                    // Get Away odds (Player 2)
                    Object awayObj = homeAway.get("Away");
                    if (awayObj instanceof org.bson.Document) {
                        org.bson.Document away = (org.bson.Document) awayObj;
                        awayOdds = away.getString("bet365");
                        if (awayOdds == null) {
                            awayOdds = away.values().stream()
                                    .filter(v -> v instanceof String)
                                    .map(v -> (String) v)
                                    .findFirst().orElse(null);
                        }
                        model.addAttribute("awayOdds", awayOdds);
                    }
                    
                    // Determine favorite for display
                    if (homeOdds != null && awayOdds != null) {
                        try {
                            double home = Double.parseDouble(homeOdds);
                            double away = Double.parseDouble(awayOdds);
                            model.addAttribute("player1Favorite", home < away);
                            model.addAttribute("player2Favorite", away < home);
                        } catch (NumberFormatException e) {
                            // Ignore parse errors
                        }
                    }
                }
            }
        });
        
        // Get Head-to-Head history from API (point-in-time: before this match)
        if (fixture.getFirstPlayerKey() != null && fixture.getSecondPlayerKey() != null) {
            LocalDate matchDate = fixture.getEventDate();
            
            // Get H2H record as it was BEFORE this match
            int[] h2hRecord = ingestionService.getH2HBeforeDate(
                    fixture.getFirstPlayerKey(), 
                    fixture.getSecondPlayerKey(), 
                    matchDate);
            model.addAttribute("p1H2hWins", h2hRecord[0]);
            model.addAttribute("p2H2hWins", h2hRecord[1]);
            
            // Get H2H match list (filtered to before this match)
            List<org.bson.Document> h2hMatches = ingestionService.getH2HMatches(
                    fixture.getFirstPlayerKey(), 
                    fixture.getSecondPlayerKey(),
                    matchDate);
            
            // Sort by date descending and limit to 10
            h2hMatches.sort((a, b) -> {
                String dateA = a.getString("event_date");
                String dateB = b.getString("event_date");
                if (dateA == null) return 1;
                if (dateB == null) return -1;
                return dateB.compareTo(dateA);
            });
            if (h2hMatches.size() > 10) {
                h2hMatches = h2hMatches.subList(0, 10);
            }
            
            model.addAttribute("h2hMatches", h2hMatches);
            model.addAttribute("h2hTotal", h2hRecord[0] + h2hRecord[1]);
        }
        
        // Get recent form for Player 1 (last 5 matches before this match)
        if (fixture.getFirstPlayerKey() != null && fixture.getEventDate() != null) {
            String player1Key = fixture.getFirstPlayerKey();
            List<FixtureDocument> p1Recent = fixtureRepository.findRecentMatchesByPlayer(
                    player1Key, fixture.getEventDate());
            p1Recent = p1Recent.stream()
                    .filter(m -> !m.getEventKey().equals(eventKey))
                    .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                    .limit(5)
                    .collect(Collectors.toList());
            
            model.addAttribute("p1RecentForm", p1Recent);
            long p1Wins = p1Recent.stream()
                    .filter(m -> isWinner(m, player1Key))
                    .count();
            model.addAttribute("p1RecentWins", p1Wins);
        }
        
        // Get recent form for Player 2 (last 5 matches before this match)
        if (fixture.getSecondPlayerKey() != null && fixture.getEventDate() != null) {
            String player2Key = fixture.getSecondPlayerKey();
            List<FixtureDocument> p2Recent = fixtureRepository.findRecentMatchesByPlayer(
                    player2Key, fixture.getEventDate());
            p2Recent = p2Recent.stream()
                    .filter(m -> !m.getEventKey().equals(eventKey))
                    .sorted((a, b) -> b.getEventDate().compareTo(a.getEventDate()))
                    .limit(5)
                    .collect(Collectors.toList());
            
            model.addAttribute("p2RecentForm", p2Recent);
            long p2Wins = p2Recent.stream()
                    .filter(m -> isWinner(m, player2Key))
                    .count();
            model.addAttribute("p2RecentWins", p2Wins);
        }
        
        // Raw JSON for debug section
        if (fixture.getRaw() != null) {
            try {
                model.addAttribute("rawJson", fixture.getRaw().toJson());
            } catch (Exception e) {
                model.addAttribute("rawJson", fixture.getRaw().toString());
            }
        }
        
        return "match";
    }

    /**
     * Players browser
     */
    @GetMapping("/players")
    public String players(
            @RequestParam(required = false) String search,
            Model model
    ) {
        List<PlayerDocument> players = playerRepository.findAll();

        // Filter by search term if provided
        if (search != null && !search.isBlank()) {
            String searchLower = search.toLowerCase();
            players = players.stream()
                    .filter(p -> (p.getPlayerName() != null && p.getPlayerName().toLowerCase().contains(searchLower)) ||
                                 (p.getCountry() != null && p.getCountry().toLowerCase().contains(searchLower)))
                    .collect(Collectors.toList());
        }

        // Sort by rank (null ranks at the end)
        players.sort((a, b) -> {
            if (a.getCurrentRank() == null) return 1;
            if (b.getCurrentRank() == null) return -1;
            return a.getCurrentRank().compareTo(b.getCurrentRank());
        });

        // Limit to top 200 for display
        if (players.size() > 200) {
            players = players.subList(0, 200);
        }

        model.addAttribute("players", players);
        model.addAttribute("playerCount", players.size());
        model.addAttribute("totalPlayerCount", playerRepository.count());
        model.addAttribute("search", search);

        return "players";
    }

    /**
     * Player details page
     */
    @GetMapping("/player/{playerKey}")
    public String playerDetails(@PathVariable String playerKey, Model model) {
        var playerOpt = playerRepository.findByPlayerKey(playerKey);
        
        if (playerOpt.isEmpty()) {
            model.addAttribute("error", "Player not found: " + playerKey);
            return "error";
        }
        
        PlayerDocument player = playerOpt.get();
        model.addAttribute("player", player);
        
        // Get player's match history
        List<FixtureDocument> matches = fixtureRepository.findByPlayerKey(playerKey);
        
        // Sort by date descending (most recent first)
        matches.sort((a, b) -> {
            if (a.getEventDate() == null) return 1;
            if (b.getEventDate() == null) return -1;
            return b.getEventDate().compareTo(a.getEventDate());
        });
        
        // Separate into finished and upcoming
        List<FixtureDocument> finishedMatches = matches.stream()
                .filter(m -> "Finished".equalsIgnoreCase(m.getStatus()))
                .limit(20)
                .collect(Collectors.toList());
        
        List<FixtureDocument> upcomingMatches = matches.stream()
                .filter(m -> !"Finished".equalsIgnoreCase(m.getStatus()) && !"Cancelled".equalsIgnoreCase(m.getStatus()))
                .limit(10)
                .collect(Collectors.toList());
        
        model.addAttribute("finishedMatches", finishedMatches);
        model.addAttribute("upcomingMatches", upcomingMatches);
        model.addAttribute("totalMatches", matches.size());
        
        // Calculate win/loss record (handle both direct playerKey and "First/Second Player" winner formats)
        long wins = finishedMatches.stream()
                .filter(m -> isWinner(m, playerKey))
                .count();
        long losses = finishedMatches.stream()
                .filter(m -> m.getWinner() != null && !isWinner(m, playerKey))
                .count();
        model.addAttribute("wins", wins);
        model.addAttribute("losses", losses);
        
        // Get player image from any recent match
        for (FixtureDocument match : matches) {
            if (match.getRaw() != null) {
                org.bson.Document raw = match.getRaw();
                if (playerKey.equals(match.getFirstPlayerKey()) && raw.containsKey("event_first_player_logo")) {
                    model.addAttribute("playerImage", raw.get("event_first_player_logo"));
                    break;
                } else if (playerKey.equals(match.getSecondPlayerKey()) && raw.containsKey("event_second_player_logo")) {
                    model.addAttribute("playerImage", raw.get("event_second_player_logo"));
                    break;
                }
            }
        }
        
        // Raw JSON for debug
        if (player.getRaw() != null) {
            try {
                model.addAttribute("rawJson", player.getRaw().toJson());
            } catch (Exception e) {
                model.addAttribute("rawJson", player.getRaw().toString());
            }
        }
        
        return "player";
    }

    /**
     * Tournament details page
     */
    @GetMapping("/tournament/{tournamentKey}")
    public String tournamentDetails(@PathVariable String tournamentKey, Model model) {
        var tournamentOpt = tournamentRepository.findByTournamentKey(tournamentKey);
        
        if (tournamentOpt.isEmpty()) {
            model.addAttribute("error", "Tournament not found: " + tournamentKey);
            return "error";
        }
        
        TournamentDocument tournament = tournamentOpt.get();
        model.addAttribute("tournament", tournament);
        
        // Look up country if not set
        String country = tournament.getCountry();
        if (country == null || country.isBlank()) {
            country = lookupTournamentCountry(tournament.getTournamentName());
        }
        model.addAttribute("tournamentCountry", country);
        
        // Get all fixtures for this tournament
        List<FixtureDocument> allFixtures = fixtureRepository.findByTournamentKey(tournamentKey);
        
        // Split into upcoming and completed
        // Completed statuses: Finished, Retired, Cancelled, Walk Over, etc.
        Set<String> completedStatuses = Set.of("finished", "retired", "cancelled", "walkover", "walk over", "abandoned");
        
        List<FixtureDocument> upcomingFixtures = allFixtures.stream()
                .filter(f -> f.getStatus() == null || !completedStatuses.contains(f.getStatus().toLowerCase()))
                .sorted((a, b) -> {
                    if (a.getEventDate() == null) return 1;
                    if (b.getEventDate() == null) return -1;
                    return a.getEventDate().compareTo(b.getEventDate());
                })
                .collect(Collectors.toList());
        
        List<FixtureDocument> completedFixtures = allFixtures.stream()
                .filter(f -> f.getStatus() != null && completedStatuses.contains(f.getStatus().toLowerCase()))
                .sorted((a, b) -> {
                    if (a.getEventDate() == null) return 1;
                    if (b.getEventDate() == null) return -1;
                    return b.getEventDate().compareTo(a.getEventDate()); // Most recent first
                })
                .collect(Collectors.toList());
        
        model.addAttribute("upcomingFixtures", upcomingFixtures);
        model.addAttribute("completedFixtures", completedFixtures);
        model.addAttribute("totalFixtures", allFixtures.size());
        
        // Get unique players in this tournament
        Set<String> playerKeys = new HashSet<>();
        for (FixtureDocument f : allFixtures) {
            if (f.getFirstPlayerKey() != null) playerKeys.add(f.getFirstPlayerKey());
            if (f.getSecondPlayerKey() != null) playerKeys.add(f.getSecondPlayerKey());
        }
        model.addAttribute("playerCount", playerKeys.size());
        
        // Tournament dates (min and max event dates)
        LocalDate minDate = allFixtures.stream()
                .map(FixtureDocument::getEventDate)
                .filter(d -> d != null)
                .min(LocalDate::compareTo)
                .orElse(null);
        LocalDate maxDate = allFixtures.stream()
                .map(FixtureDocument::getEventDate)
                .filter(d -> d != null)
                .max(LocalDate::compareTo)
                .orElse(null);
        model.addAttribute("tournamentStartDate", minDate);
        model.addAttribute("tournamentEndDate", maxDate);
        
        // Raw JSON for debug section
        if (tournament.getRaw() != null) {
            try {
                model.addAttribute("rawJson", tournament.getRaw().toJson());
            } catch (Exception e) {
                model.addAttribute("rawJson", tournament.getRaw().toString());
            }
        }
        
        return "tournament";
    }

    /**
     * Sync control page
     */
    @GetMapping("/sync")
    public String sync(Model model) {
        model.addAttribute("eventCount", eventRepository.count());
        model.addAttribute("tournamentCount", tournamentRepository.count());
        model.addAttribute("fixtureCount", fixtureRepository.count());
        model.addAttribute("playerCount", playerRepository.count());
        model.addAttribute("oddsCount", oddsRepository.count());
        return "sync";
    }

    /**
     * Handle sync actions via POST
     */
    @PostMapping("/sync")
    public String doSync(
            @RequestParam String action,
            @RequestParam(required = false) String dateStart,
            @RequestParam(required = false) String dateStop,
            Model model
    ) {
        IngestionResult result = null;
        String message = null;

        try {
            switch (action) {
                case "catalog":
                    var eventsResult = ingestionService.ingestEvents();
                    // API returns all tournaments regardless of filter, so only call once
                    var tournamentsResult = ingestionService.ingestTournaments(null);
                    message = String.format("Catalog sync: Events=%d, Tournaments=%d",
                            eventsResult.getCount(), tournamentsResult.getCount());
                    break;

                case "fixtures":
                    LocalDate start = dateStart != null ? LocalDate.parse(dateStart) : LocalDate.now().minusDays(7);
                    LocalDate stop = dateStop != null ? LocalDate.parse(dateStop) : LocalDate.now().plusDays(7);
                    result = ingestionService.ingestFixturesBatched(start, stop);
                    message = result.getMessage();
                    break;

                case "players":
                    result = ingestionService.syncPlayersFromFixtures();
                    message = result.getMessage();
                    break;

                case "odds":
                    LocalDate oddsStart = dateStart != null ? LocalDate.parse(dateStart) : LocalDate.now();
                    LocalDate oddsStop = dateStop != null ? LocalDate.parse(dateStop) : LocalDate.now().plusDays(7);
                    result = ingestionService.ingestOddsBatched(oddsStart, oddsStop);
                    message = result.getMessage();
                    break;

                case "cleanup":
                    result = ingestionService.cleanupNonSinglesData();
                    message = result.getMessage();
                    break;

                default:
                    message = "Unknown action: " + action;
            }
        } catch (Exception e) {
            message = "Error: " + e.getMessage();
        }

        model.addAttribute("syncMessage", message);
        model.addAttribute("eventCount", eventRepository.count());
        model.addAttribute("tournamentCount", tournamentRepository.count());
        model.addAttribute("fixtureCount", fixtureRepository.count());
        model.addAttribute("playerCount", playerRepository.count());
        model.addAttribute("oddsCount", oddsRepository.count());

        return "sync";
    }
    
    /**
     * Calculate set scores from pointbypoint data.
     * Looks at the final game of each set to determine the score.
     */
    private java.util.List<String> calculateSetScores(java.util.List<org.bson.Document> games) {
        java.util.List<String> setScores = new java.util.ArrayList<>();
        java.util.Map<String, String> lastScorePerSet = new java.util.LinkedHashMap<>();
        
        for (org.bson.Document game : games) {
            String setNumber = game.getString("set_number");
            String score = game.getString("score");
            if (setNumber != null && score != null) {
                lastScorePerSet.put(setNumber, score);
            }
        }
        
        // Convert scores like "6 - 4" to proper format
        for (java.util.Map.Entry<String, String> entry : lastScorePerSet.entrySet()) {
            String score = entry.getValue();
            if (score != null) {
                // Format: "6 - 4" -> "6-4"
                score = score.replace(" ", "");
                setScores.add(score);
            }
        }
        
        return setScores;
    }
    
    /**
     * Look up country for a tournament by checking against known tournament names.
     * Returns null if no match found.
     */
    private String lookupTournamentCountry(String tournamentName) {
        if (tournamentName == null || tournamentName.isBlank()) {
            return null;
        }
        
        // Check if tournament name contains any of our known keywords
        for (Map.Entry<String, String> entry : TOURNAMENT_COUNTRIES.entrySet()) {
            if (tournamentName.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    /**
     * Player comparison page
     */
    @GetMapping("/compare")
    public String compare(
            @RequestParam(required = false) String player1,
            @RequestParam(required = false) String player2,
            Model model
    ) {
        // Get all players for selection dropdowns (sorted by rank, then name)
        List<PlayerDocument> allPlayers = playerRepository.findAll();
        allPlayers.sort((a, b) -> {
            if (a.getCurrentRank() == null && b.getCurrentRank() == null) {
                return (a.getPlayerName() != null ? a.getPlayerName() : "").compareTo(b.getPlayerName() != null ? b.getPlayerName() : "");
            }
            if (a.getCurrentRank() == null) return 1;
            if (b.getCurrentRank() == null) return -1;
            return a.getCurrentRank().compareTo(b.getCurrentRank());
        });
        model.addAttribute("allPlayers", allPlayers);
        
        // If both players selected, show comparison
        if (player1 != null && !player1.isBlank() && player2 != null && !player2.isBlank()) {
            var p1Opt = playerRepository.findByPlayerKey(player1);
            var p2Opt = playerRepository.findByPlayerKey(player2);
            
            if (p1Opt.isPresent() && p2Opt.isPresent()) {
                PlayerDocument p1 = p1Opt.get();
                PlayerDocument p2 = p2Opt.get();
                
                model.addAttribute("player1", p1);
                model.addAttribute("player2", p2);
                model.addAttribute("player1Key", player1);
                model.addAttribute("player2Key", player2);
                
                // Get player images from recent fixtures
                String p1Image = getPlayerImage(player1);
                String p2Image = getPlayerImage(player2);
                model.addAttribute("player1Image", p1Image);
                model.addAttribute("player2Image", p2Image);
                
                // Head-to-head matches
                List<FixtureDocument> h2hMatches = fixtureRepository.findH2HMatches(player1, player2);
                h2hMatches.sort((a, b) -> {
                    if (a.getEventDate() == null) return 1;
                    if (b.getEventDate() == null) return -1;
                    return b.getEventDate().compareTo(a.getEventDate()); // Most recent first
                });
                model.addAttribute("h2hMatches", h2hMatches);
                
                // Calculate H2H wins
                int p1Wins = 0;
                int p2Wins = 0;
                for (FixtureDocument match : h2hMatches) {
                    if (player1.equals(match.getWinner())) {
                        p1Wins++;
                    } else if (player2.equals(match.getWinner())) {
                        p2Wins++;
                    } else if ("First Player".equals(match.getWinner()) && player1.equals(match.getFirstPlayerKey())) {
                        p1Wins++;
                    } else if ("First Player".equals(match.getWinner()) && player2.equals(match.getFirstPlayerKey())) {
                        p2Wins++;
                    } else if ("Second Player".equals(match.getWinner()) && player1.equals(match.getSecondPlayerKey())) {
                        p1Wins++;
                    } else if ("Second Player".equals(match.getWinner()) && player2.equals(match.getSecondPlayerKey())) {
                        p2Wins++;
                    }
                }
                model.addAttribute("p1H2HWins", p1Wins);
                model.addAttribute("p2H2HWins", p2Wins);
                
                // Recent form for each player (last 10 finished matches)
                List<FixtureDocument> p1RecentMatches = fixtureRepository.findByPlayerKey(player1).stream()
                        .filter(m -> "Finished".equalsIgnoreCase(m.getStatus()))
                        .sorted((a, b) -> b.getEventDate() != null ? b.getEventDate().compareTo(a.getEventDate() != null ? a.getEventDate() : LocalDate.MIN) : 1)
                        .limit(10)
                        .collect(Collectors.toList());
                
                List<FixtureDocument> p2RecentMatches = fixtureRepository.findByPlayerKey(player2).stream()
                        .filter(m -> "Finished".equalsIgnoreCase(m.getStatus()))
                        .sorted((a, b) -> b.getEventDate() != null ? b.getEventDate().compareTo(a.getEventDate() != null ? a.getEventDate() : LocalDate.MIN) : 1)
                        .limit(10)
                        .collect(Collectors.toList());
                
                model.addAttribute("p1RecentMatches", p1RecentMatches);
                model.addAttribute("p2RecentMatches", p2RecentMatches);
                
                // Calculate recent form W-L
                long p1RecentWins = p1RecentMatches.stream().filter(m -> isWinner(m, player1)).count();
                long p2RecentWins = p2RecentMatches.stream().filter(m -> isWinner(m, player2)).count();
                model.addAttribute("p1RecentWins", p1RecentWins);
                model.addAttribute("p1RecentLosses", p1RecentMatches.size() - p1RecentWins);
                model.addAttribute("p2RecentWins", p2RecentWins);
                model.addAttribute("p2RecentLosses", p2RecentMatches.size() - p2RecentWins);
                
                // Surface statistics from raw player data
                Map<String, int[]> p1SurfaceStats = extractSurfaceStats(p1);
                Map<String, int[]> p2SurfaceStats = extractSurfaceStats(p2);
                model.addAttribute("p1SurfaceStats", p1SurfaceStats);
                model.addAttribute("p2SurfaceStats", p2SurfaceStats);
            }
        }
        
        return "compare";
    }
    
    /**
     * Get player image from fixture data
     */
    private String getPlayerImage(String playerKey) {
        List<FixtureDocument> matches = fixtureRepository.findByPlayerKey(playerKey);
        for (FixtureDocument match : matches) {
            if (match.getRaw() != null) {
                org.bson.Document raw = match.getRaw();
                if (playerKey.equals(match.getFirstPlayerKey()) && raw.containsKey("event_first_player_logo")) {
                    return raw.getString("event_first_player_logo");
                } else if (playerKey.equals(match.getSecondPlayerKey()) && raw.containsKey("event_second_player_logo")) {
                    return raw.getString("event_second_player_logo");
                }
            }
        }
        return null;
    }
    
    /**
     * Check if player is the winner of a match
     */
    private boolean isWinner(FixtureDocument match, String playerKey) {
        if (playerKey.equals(match.getWinner())) return true;
        if ("First Player".equals(match.getWinner()) && playerKey.equals(match.getFirstPlayerKey())) return true;
        if ("Second Player".equals(match.getWinner()) && playerKey.equals(match.getSecondPlayerKey())) return true;
        return false;
    }
    
    /**
     * Extract surface statistics from player's raw data (stats array)
     */
    private Map<String, int[]> extractSurfaceStats(PlayerDocument player) {
        Map<String, int[]> stats = new LinkedHashMap<>();
        stats.put("Hard", new int[]{0, 0});
        stats.put("Clay", new int[]{0, 0});
        stats.put("Grass", new int[]{0, 0});
        
        if (player.getRaw() == null) return stats;
        
        Object statsObj = player.getRaw().get("stats");
        if (!(statsObj instanceof List)) return stats;
        
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
        
        // Get stats from latest season
        if (latestSeason != null) {
            for (org.bson.Document stat : statsList) {
                String type = stat.getString("type");
                String season = stat.getString("season");
                if ("singles".equalsIgnoreCase(type) && latestSeason.equals(season)) {
                    stats.put("Hard", new int[]{
                            parseIntSafe(stat.getString("hard_won")),
                            parseIntSafe(stat.getString("hard_lost"))
                    });
                    stats.put("Clay", new int[]{
                            parseIntSafe(stat.getString("clay_won")),
                            parseIntSafe(stat.getString("clay_lost"))
                    });
                    stats.put("Grass", new int[]{
                            parseIntSafe(stat.getString("grass_won")),
                            parseIntSafe(stat.getString("grass_lost"))
                    });
                    break;
                }
            }
        }
        
        return stats;
    }
    
    private int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ============ PREDICTION VERIFICATION ============

    /**
     * Prediction verification page - upload CSV to verify predictions against actual results.
     */
    @GetMapping("/verify")
    public String verifyPage(Model model) {
        return "verify";
    }

    /**
     * Process uploaded CSV and show verification results.
     * @param fetchFromApi If true, fetch fresh data from API Tennis for missing/unfinished matches
     */
    @PostMapping("/verify")
    public String verifyPredictions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fetchFromApi", defaultValue = "false") boolean fetchFromApi,
            Model model
    ) {
        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a CSV file to upload");
            return "verify";
        }
        
        try {
            VerificationReport report = verificationService.verifyPredictions(file, fetchFromApi);
            model.addAttribute("report", report);
            model.addAttribute("hasReport", true);
        } catch (Exception e) {
            model.addAttribute("error", "Error processing file: " + e.getMessage());
        }
        
        return "verify";
    }

    // ============ FIXTURE MANAGEMENT ============

    /**
     * Reset fixture results from a given date onwards.
     * This is useful for re-testing predictions against matches that have already occurred.
     */
    @PostMapping("/fixtures/reset-results")
    public String resetResults(
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate fromDate,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes
    ) {
        try {
            int resetCount = ingestionService.resetResultsFromDate(fromDate);
            redirectAttributes.addFlashAttribute("successMessage", 
                    String.format("✓ Reset %d fixtures from %s onwards", resetCount, fromDate));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", 
                    "Error resetting results: " + e.getMessage());
        }
        return "redirect:/ui/sync";
    }
}

