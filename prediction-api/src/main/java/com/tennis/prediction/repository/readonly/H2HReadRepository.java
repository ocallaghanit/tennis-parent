package com.tennis.prediction.repository.readonly;

import com.tennis.prediction.model.readonly.H2HDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read-only repository for head-to-head data.
 */
@Repository
public interface H2HReadRepository extends MongoRepository<H2HDocument, String> {

    Optional<H2HDocument> findByPlayerKey1AndPlayerKey2(String playerKey1, String playerKey2);

    /**
     * Find H2H between two players regardless of order
     */
    default Optional<H2HDocument> findH2H(String player1, String player2) {
        // H2H documents are stored with consistent ordering (p1 < p2 alphabetically)
        String p1 = player1.compareTo(player2) < 0 ? player1 : player2;
        String p2 = player1.compareTo(player2) < 0 ? player2 : player1;
        return findByPlayerKey1AndPlayerKey2(p1, p2);
    }
}

