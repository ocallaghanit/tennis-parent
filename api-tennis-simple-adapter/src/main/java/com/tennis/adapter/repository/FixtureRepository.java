package com.tennis.adapter.repository;

import com.tennis.adapter.model.FixtureDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FixtureRepository extends MongoRepository<FixtureDocument, String> {
    
    Optional<FixtureDocument> findByEventKey(String eventKey);
    
    List<FixtureDocument> findByTournamentKey(String tournamentKey);
    
    List<FixtureDocument> findByEventDateBetween(LocalDate start, LocalDate end);
    
    List<FixtureDocument> findByTournamentKeyAndEventDateBetween(String tournamentKey, LocalDate start, LocalDate end);
    
    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ] }")
    List<FixtureDocument> findByPlayerKey(String playerKey);
    
    @Query("{ $or: [ {'firstPlayerKey': ?0}, {'secondPlayerKey': ?0} ], 'eventDate': { $gte: ?1, $lte: ?2 } }")
    List<FixtureDocument> findByPlayerKeyAndDateRange(String playerKey, LocalDate start, LocalDate end);
}

