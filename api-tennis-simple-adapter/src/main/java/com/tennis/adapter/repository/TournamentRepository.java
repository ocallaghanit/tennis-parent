package com.tennis.adapter.repository;

import com.tennis.adapter.model.TournamentDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TournamentRepository extends MongoRepository<TournamentDocument, String> {
    Optional<TournamentDocument> findByTournamentKey(String tournamentKey);
    List<TournamentDocument> findByEventTypeKey(String eventTypeKey);
}

