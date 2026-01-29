package com.tennis.prediction.repository;

import com.tennis.prediction.model.OwlRatingDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for OWL Rating documents.
 */
@Repository
public interface OwlRatingRepository extends MongoRepository<OwlRatingDocument, String> {

    Optional<OwlRatingDocument> findByPlayerKey(String playerKey);

    List<OwlRatingDocument> findByPlayerKeyIn(List<String> playerKeys);

    // Leaderboard - top players by rating
    List<OwlRatingDocument> findAllByOrderByRatingDesc(Pageable pageable);

    // Find by OWL rank
    List<OwlRatingDocument> findAllByOrderByOwlRankAsc(Pageable pageable);

    // Players with minimum matches played
    @Query("{ 'matchesPlayed': { $gte: ?0 } }")
    List<OwlRatingDocument> findByMinMatchesPlayedOrderByRatingDesc(int minMatches, Pageable pageable);

    // Search by name
    @Query("{ 'playerName': { $regex: ?0, $options: 'i' } }")
    List<OwlRatingDocument> searchByName(String namePattern);

    // Count players
    long countByMatchesPlayedGreaterThanEqual(int minMatches);

    // Find players in rating range
    @Query("{ 'rating': { $gte: ?0, $lte: ?1 } }")
    List<OwlRatingDocument> findByRatingBetween(double minRating, double maxRating);

    // Find all with at least one match
    List<OwlRatingDocument> findByMatchesPlayedGreaterThan(int minMatches);
}

