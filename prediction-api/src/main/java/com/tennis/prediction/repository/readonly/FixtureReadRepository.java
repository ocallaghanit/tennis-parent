package com.tennis.prediction.repository.readonly;

import com.tennis.prediction.model.readonly.FixtureDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-only repository for fixtures.
 * Note: Write operations will fail with MongoDB authorization error.
 */
@Repository
public interface FixtureReadRepository extends MongoRepository<FixtureDocument, String> {

    Optional<FixtureDocument> findByEventKey(String eventKey);

    List<FixtureDocument> findByTournamentKey(String tournamentKey);

    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 } }")
    List<FixtureDocument> findByDateRange(LocalDate start, LocalDate end);

    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 }, 'status': { $in: ['Not Started', 'not started', 'NS', 'Scheduled', ''] }, 'winner': null }")
    List<FixtureDocument> findUpcomingByDateRange(LocalDate start, LocalDate end);
    
    /**
     * Find all matches in date range regardless of status (for dropdown population)
     */
    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 }, 'winner': null }")
    List<FixtureDocument> findUnfinishedByDateRange(LocalDate start, LocalDate end);

    @Query("{ 'eventDate': { $gte: ?0, $lte: ?1 }, 'status': 'Finished' }")
    List<FixtureDocument> findFinishedByDateRange(LocalDate start, LocalDate end);

    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ] }")
    List<FixtureDocument> findByPlayerKey(String playerKey);

    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ], 'status': 'Finished', 'eventDate': { $lt: ?1 } }")
    List<FixtureDocument> findRecentMatchesByPlayer(String playerKey, LocalDate beforeDate);

    @Query("{ $or: [ " +
           "  { 'firstPlayerKey': ?0, 'secondPlayerKey': ?1 }, " +
           "  { 'firstPlayerKey': ?1, 'secondPlayerKey': ?0 } " +
           "], 'status': 'Finished' }")
    List<FixtureDocument> findH2HMatches(String playerKey1, String playerKey2);
}

