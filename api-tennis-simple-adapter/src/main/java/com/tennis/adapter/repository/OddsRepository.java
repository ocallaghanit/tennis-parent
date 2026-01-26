package com.tennis.adapter.repository;

import com.tennis.adapter.model.OddsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OddsRepository extends MongoRepository<OddsDocument, String> {
    Optional<OddsDocument> findByMatchKey(String matchKey);
    List<OddsDocument> findByEventDateBetween(LocalDate start, LocalDate end);
    List<OddsDocument> findByTournamentKey(String tournamentKey);
}

