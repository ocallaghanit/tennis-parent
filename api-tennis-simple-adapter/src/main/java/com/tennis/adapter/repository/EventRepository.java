package com.tennis.adapter.repository;

import com.tennis.adapter.model.EventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface EventRepository extends MongoRepository<EventDocument, String> {
    Optional<EventDocument> findByEventKey(String eventKey);
}

