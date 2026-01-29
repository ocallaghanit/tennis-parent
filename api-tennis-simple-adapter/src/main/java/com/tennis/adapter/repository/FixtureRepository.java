package com.tennis.adapter.repository;

import com.tennis.adapter.model.FixtureDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixtureRepository extends MongoRepository<FixtureDocument, String> {
    
    Optional<FixtureDocument> findByEventKey(String eventKey);
    
    List<FixtureDocument> findByTournamentKey(String tournamentKey);
    
    /**
     * Find fixtures by date range (inclusive on both ends).
     * Note: Spring Data's "Between" uses exclusive bounds, so we use a custom query.
     */
    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 } }")
    List<FixtureDocument> findByEventDateBetween(LocalDate start, LocalDate end);
    
    /**
     * Find fixtures by tournament and date range (inclusive on both ends).
     */
    @Query("{ 'tournamentKey': ?0, 'eventDate': { $gte: ?1, $lte: ?2 } }")
    List<FixtureDocument> findByTournamentKeyAndEventDateBetween(String tournamentKey, LocalDate start, LocalDate end);
    
    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ] }")
    List<FixtureDocument> findByPlayerKey(String playerKey);
    
    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ], 'eventDate': { $gte: ?1, $lte: ?2 } }")
    List<FixtureDocument> findByPlayerKeyAndDateRange(String playerKey, LocalDate start, LocalDate end);
    
    /**
     * Find head-to-head matches between two players (finished matches only)
     */
    @Query("{ $or: [ " +
           "  { 'firstPlayerKey': ?0, 'secondPlayerKey': ?1 }, " +
           "  { 'firstPlayerKey': ?1, 'secondPlayerKey': ?0 } " +
           "], 'status': 'Finished' }")
    List<FixtureDocument> findH2HMatches(String playerKey1, String playerKey2);
    
    /**
     * Find recent matches for a player (finished matches, sorted by date desc)
     */
    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ], 'status': 'Finished', 'eventDate': { $lt: ?1 } }")
    List<FixtureDocument> findRecentMatchesByPlayer(String playerKey, LocalDate beforeDate);

    // =============== PAGINATED QUERIES ===============

    /**
     * Paginated fixtures by date range (inclusive on both ends)
     */
    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 } }")
    Page<FixtureDocument> findByEventDateBetween(LocalDate start, LocalDate end, Pageable pageable);

    /**
     * Paginated fixtures by tournament
     */
    Page<FixtureDocument> findByTournamentKey(String tournamentKey, Pageable pageable);

    /**
     * Paginated fixtures by status
     */
    Page<FixtureDocument> findByStatus(String status, Pageable pageable);

    /**
     * Paginated fixtures by date range and status (inclusive on both ends)
     */
    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 }, 'status': ?2 }")
    Page<FixtureDocument> findByEventDateBetweenAndStatus(LocalDate start, LocalDate end, String status, Pageable pageable);

    /**
     * Paginated fixtures by tournament and status
     */
    Page<FixtureDocument> findByTournamentKeyAndStatus(String tournamentKey, String status, Pageable pageable);

    /**
     * Paginated fixtures by tournament, date range, and status (inclusive on both ends)
     */
    @Query("{ 'tournamentKey': ?0, 'eventDate': { $gte: ?1, $lte: ?2 }, 'status': ?3 }")
    Page<FixtureDocument> findByTournamentKeyAndEventDateBetweenAndStatus(
            String tournamentKey, LocalDate start, LocalDate end, String status, Pageable pageable);

    /**
     * Paginated fixtures by tournament and date range (inclusive on both ends)
     */
    @Query("{ 'tournamentKey': ?0, 'eventDate': { $gte: ?1, $lte: ?2 } }")
    Page<FixtureDocument> findByTournamentKeyAndEventDateBetween(
            String tournamentKey, LocalDate start, LocalDate end, Pageable pageable);

    /**
     * Find fixtures updated after a certain time (for sync)
     */
    List<FixtureDocument> findByUpdatedAtAfter(Instant since);

    /**
     * Find fixtures by multiple event keys (bulk lookup)
     */
    List<FixtureDocument> findByEventKeyIn(List<String> eventKeys);

    /**
     * Find fixtures from a date onwards (inclusive)
     */
    @Query("{ 'eventDate': { $gte: ?0 } }")
    List<FixtureDocument> findByEventDateGreaterThanEqual(LocalDate date);
}

