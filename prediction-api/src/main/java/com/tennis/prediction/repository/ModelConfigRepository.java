package com.tennis.prediction.repository;

import com.tennis.prediction.model.ModelConfigDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Full read/write repository for model configurations.
 */
@Repository
public interface ModelConfigRepository extends MongoRepository<ModelConfigDocument, String> {

    Optional<ModelConfigDocument> findByModelId(String modelId);

    List<ModelConfigDocument> findByActive(boolean active);

    Optional<ModelConfigDocument> findFirstByActiveTrue();
}

