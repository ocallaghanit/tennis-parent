package com.tennis.prediction.repository;

import com.tennis.prediction.model.PredictionResultDocument;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Full read/write repository for prediction results.
 */
@Repository
public interface PredictionResultRepository extends MongoRepository<PredictionResultDocument, String> {

    Optional<PredictionResultDocument> findByMatchKeyAndModelId(String matchKey, String modelId);

    List<PredictionResultDocument> findByModelId(String modelId);

    @Query("{ 'matchDate': { $gte: ?0, $lte: ?1 }, 'modelId': ?2 }")
    List<PredictionResultDocument> findByDateRangeAndModel(LocalDate start, LocalDate end, String modelId);

    long countByModelIdAndCorrect(String modelId, boolean correct);

    /**
     * Calculate accuracy for a model
     */
    default double calculateAccuracy(String modelId) {
        long correct = countByModelIdAndCorrect(modelId, true);
        long total = countByModelIdAndCorrect(modelId, true) + countByModelIdAndCorrect(modelId, false);
        return total > 0 ? (double) correct / total : 0.0;
    }
}

