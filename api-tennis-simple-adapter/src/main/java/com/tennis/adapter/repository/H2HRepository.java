package com.tennis.adapter.repository;

import com.tennis.adapter.model.H2HDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface H2HRepository extends MongoRepository<H2HDocument, String> {
    
    /**
     * Find H2H by both player keys (order doesn't matter due to how we store them)
     */
    @Query("{ $or: [ " +
            "  { 'playerKey1': ?0, 'playerKey2': ?1 }, " +
            "  { 'playerKey1': ?1, 'playerKey2': ?0 } " +
            "] }")
    Optional<H2HDocument> findByPlayerKeys(String playerKey1, String playerKey2);
}

