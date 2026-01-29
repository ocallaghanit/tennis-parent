package com.tennis.prediction.repository;

import com.tennis.prediction.model.PredictionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Full read/write repository for predictions.
 */
@Repository
public interface PredictionRepository extends MongoRepository<PredictionDocument, String> {

    Optional<PredictionDocument> findByMatchKeyAndModelId(String matchKey, String modelId);

    List<PredictionDocument> findByMatchKey(String matchKey);

    List<PredictionDocument> findByModelId(String modelId);

    @Query("{ 'matchDate': { $gte: ?0, $lte: ?1 } }")
    List<PredictionDocument> findByDateRange(LocalDate start, LocalDate end);

    @Query("{ 'matchDate': { $gte: ?0, $lte: ?1 }, 'modelId': ?2 }")
    List<PredictionDocument> findByDateRangeAndModel(LocalDate start, LocalDate end, String modelId);

    List<PredictionDocument> findByPlayer1KeyOrPlayer2Key(String player1Key, String player2Key);
}

