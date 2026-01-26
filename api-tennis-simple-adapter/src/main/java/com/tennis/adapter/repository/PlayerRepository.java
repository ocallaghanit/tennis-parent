package com.tennis.adapter.repository;

import com.tennis.adapter.model.PlayerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PlayerRepository extends MongoRepository<PlayerDocument, String> {
    Optional<PlayerDocument> findByPlayerKey(String playerKey);
}

