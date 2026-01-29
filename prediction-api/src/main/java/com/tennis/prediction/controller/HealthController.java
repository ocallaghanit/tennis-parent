package com.tennis.prediction.controller;

import com.tennis.prediction.repository.PredictionRepository;
import com.tennis.prediction.repository.readonly.FixtureReadRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "API health and status")
public class HealthController {

    private final FixtureReadRepository fixtureRepository;
    private final PredictionRepository predictionRepository;

    public HealthController(
            FixtureReadRepository fixtureRepository,
            PredictionRepository predictionRepository
    ) {
        this.fixtureRepository = fixtureRepository;
        this.predictionRepository = predictionRepository;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check API and database connectivity")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now());

        // Test read access to adapter data
        try {
            long fixtureCount = fixtureRepository.count();
            health.put("adapterDataAccess", "OK");
            health.put("fixtureCount", fixtureCount);
        } catch (Exception e) {
            health.put("adapterDataAccess", "ERROR: " + e.getMessage());
        }

        // Test write access to prediction data
        try {
            long predictionCount = predictionRepository.count();
            health.put("predictionDataAccess", "OK");
            health.put("predictionCount", predictionCount);
        } catch (Exception e) {
            health.put("predictionDataAccess", "ERROR: " + e.getMessage());
        }

        return health;
    }
}

