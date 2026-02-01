package com.tennis.adapter.repository;

import com.tennis.adapter.model.PlayerDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PlayerRepository extends MongoRepository<PlayerDocument, String> {
    
    Optional<PlayerDocument> findByPlayerKey(String playerKey);

    /**
     * Paginated player list
     */
    Page<PlayerDocument> findAll(Pageable pageable);

    /**
     * Search players by name (case-insensitive)
     */
    @Query("{ 'playerName': { $regex: ?0, $options: 'i' } }")
    List<PlayerDocument> findByPlayerNameContaining(String namePattern);

    /**
     * Paginated search by name
     */
    @Query("{ 'playerName': { $regex: ?0, $options: 'i' } }")
    Page<PlayerDocument> findByPlayerNameContaining(String namePattern, Pageable pageable);

    /**
     * Find players by country
     */
    List<PlayerDocument> findByCountry(String country);

    /**
     * Paginated players by country
     */
    Page<PlayerDocument> findByCountry(String country, Pageable pageable);

    /**
     * Find players with rank within range
     */
    List<PlayerDocument> findByCurrentRankBetween(Integer minRank, Integer maxRank);

    /**
     * Paginated players by rank range
     */
    Page<PlayerDocument> findByCurrentRankBetween(Integer minRank, Integer maxRank, Pageable pageable);

    /**
     * Find players by multiple keys (bulk lookup)
     */
    List<PlayerDocument> findByPlayerKeyIn(List<String> playerKeys);

    /**
     * Find players updated after a certain time (for sync)
     */
    List<PlayerDocument> findByUpdatedAtAfter(Instant since);

    /**
     * Find players with non-null rank, sorted by rank
     */
    Page<PlayerDocument> findByCurrentRankNotNull(Pageable pageable);

    /**
     * Find players fetched after a certain time.
     * Used to check which players are up-to-date without loading full documents.
     */
    @Query(value = "{ 'fetchedAt': { $gt: ?0 } }", fields = "{ 'playerKey': 1 }")
    List<PlayerDocument> findPlayerKeysByFetchedAtAfter(Instant threshold);
    
    /**
     * Get all player keys (lightweight query without raw data)
     */
    @Query(value = "{}", fields = "{ 'playerKey': 1 }")
    List<PlayerDocument> findAllPlayerKeysOnly();
}

