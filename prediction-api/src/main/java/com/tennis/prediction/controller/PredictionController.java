package com.tennis.prediction.controller;

import com.tennis.prediction.model.ModelConfigDocument;
import com.tennis.prediction.model.PredictionDocument;
import com.tennis.prediction.model.readonly.FixtureDocument;
import com.tennis.prediction.repository.PredictionRepository;
import com.tennis.prediction.service.BacktestService;
import com.tennis.prediction.service.ModelService;
import com.tennis.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/predictions")
@Tag(name = "Predictions", description = "Match prediction endpoints")
public class PredictionController {

    private final PredictionRepository predictionRepository;
    private final PredictionService predictionService;
    private final ModelService modelService;
    private final BacktestService backtestService;

    public PredictionController(
            PredictionRepository predictionRepository,
            PredictionService predictionService,
            ModelService modelService,
            BacktestService backtestService
    ) {
        this.predictionRepository = predictionRepository;
        this.predictionService = predictionService;
        this.modelService = modelService;
        this.backtestService = backtestService;
    }

    // ============ PREDICTIONS ============

    @GetMapping
    @Operation(summary = "Get all predictions", description = "Retrieve all stored predictions")
    public List<PredictionDocument> getAllPredictions() {
        return predictionRepository.findAll();
    }

    @GetMapping("/match/{matchKey}")
    @Operation(summary = "Get predictions for a match", description = "Retrieve all predictions for a specific match")
    public List<PredictionDocument> getPredictionsForMatch(@PathVariable String matchKey) {
        return predictionRepository.findByMatchKey(matchKey);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming matches", description = "Retrieve upcoming matches that can be predicted")
    public List<FixtureDocument> getUpcomingMatches(
            @RequestParam(defaultValue = "7") int days
    ) {
        return predictionService.getUpcomingMatches(days);
    }

    @PostMapping("/predict/{matchKey}")
    @Operation(summary = "Create prediction for a match", description = "Generate and store a prediction using the prediction model")
    public ResponseEntity<PredictionDocument> predictMatch(
            @PathVariable String matchKey,
            @RequestParam(defaultValue = "balanced") String modelId
    ) {
        try {
            PredictionDocument prediction = predictionService.predict(matchKey, modelId);
            return ResponseEntity.ok(prediction);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/predict-upcoming")
    @Operation(summary = "Predict all upcoming matches", description = "Generate predictions for all upcoming matches")
    public List<PredictionDocument> predictUpcoming(
            @RequestParam(defaultValue = "balanced") String modelId,
            @RequestParam(defaultValue = "7") int days
    ) {
        return predictionService.predictUpcoming(modelId, days);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a prediction", description = "Remove a prediction by ID")
    public ResponseEntity<Void> deletePrediction(@PathVariable String id) {
        if (!predictionRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        predictionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ============ MODELS ============

    @GetMapping("/models")
    @Operation(summary = "Get all models", description = "Retrieve all available prediction models")
    public List<ModelConfigDocument> getAllModels() {
        return modelService.getAllModels();
    }

    @GetMapping("/models/{modelId}")
    @Operation(summary = "Get model by ID", description = "Retrieve a specific prediction model")
    public ResponseEntity<ModelConfigDocument> getModel(@PathVariable String modelId) {
        return modelService.getModel(modelId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/models")
    @Operation(summary = "Create custom model", description = "Create a new custom prediction model")
    public ModelConfigDocument createModel(@RequestBody CreateModelRequest request) {
        return modelService.createCustomModel(
                request.modelId(),
                request.name(),
                request.description(),
                request.weights()
        );
    }

    @PutMapping("/models/{modelId}/activate")
    @Operation(summary = "Set active model", description = "Set a model as the active default model")
    public ModelConfigDocument setActiveModel(@PathVariable String modelId) {
        return modelService.setActiveModel(modelId);
    }

    @DeleteMapping("/models/{modelId}")
    @Operation(summary = "Delete custom model", description = "Delete a custom model (built-in models cannot be deleted)")
    public ResponseEntity<Void> deleteModel(@PathVariable String modelId) {
        try {
            if (modelService.deleteModel(modelId)) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ============ BACKTESTING ============

    @PostMapping("/backtest")
    @Operation(summary = "Run backtest", description = "Run a backtest for a model on historical data")
    public BacktestService.BacktestResult runBacktest(
            @RequestParam(defaultValue = "balanced") String modelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return backtestService.runBacktest(modelId, startDate, endDate);
    }

    @PostMapping("/backtest/compare")
    @Operation(summary = "Compare models", description = "Run backtests for multiple models on the same date range")
    public Map<String, BacktestService.BacktestResult> compareModels(
            @RequestParam List<String> modelIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return backtestService.compareModels(modelIds, startDate, endDate);
    }

    @GetMapping("/backtest/confidence/{modelId}")
    @Operation(summary = "Analyze by confidence", description = "Analyze prediction accuracy by confidence level")
    public Map<String, BacktestService.ConfidenceBucket> analyzeByConfidence(@PathVariable String modelId) {
        return backtestService.analyzeByConfidence(modelId);
    }

    // ============ REQUEST/RESPONSE CLASSES ============

    public record CreateModelRequest(
            String modelId,
            String name,
            String description,
            Map<String, Double> weights
    ) {}
}

