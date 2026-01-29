package com.tennis.prediction.repository.readonly;

import com.tennis.prediction.model.readonly.OddsDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Read-only repository for odds.
 */
@Repository
public interface OddsReadRepository extends MongoRepository<OddsDocument, String> {

    Optional<OddsDocument> findByMatchKey(String matchKey);

    @Query("{ 'matchDate': { $gte: ?0, $lte: ?1 } }")
    List<OddsDocument> findByDateRange(LocalDate start, LocalDate end);

    List<OddsDocument> findByMatchKeyIn(List<String> matchKeys);
}

