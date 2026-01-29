package com.tennis.prediction.repository.readonly;

import com.tennis.prediction.model.readonly.TournamentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only repository for tournaments.
 */
@Repository
public interface TournamentReadRepository extends MongoRepository<TournamentDocument, String> {

    Optional<TournamentDocument> findByTournamentKey(String tournamentKey);

    List<TournamentDocument> findBySurface(String surface);

    List<TournamentDocument> findByCountry(String country);
}

