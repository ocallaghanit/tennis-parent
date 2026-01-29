package com.tennis.prediction.repository.readonly;

import com.tennis.prediction.model.readonly.PlayerDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Read-only repository for players.
 */
@Repository
public interface PlayerReadRepository extends MongoRepository<PlayerDocument, String> {

    Optional<PlayerDocument> findByPlayerKey(String playerKey);

    List<PlayerDocument> findByPlayerKeyIn(List<String> playerKeys);

    List<PlayerDocument> findByCurrentRankNotNullOrderByCurrentRankAsc();

    List<PlayerDocument> findByCountry(String country);
}

