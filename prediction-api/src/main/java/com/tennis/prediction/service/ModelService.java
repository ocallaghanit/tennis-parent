package com.tennis.prediction.service;

import com.tennis.prediction.model.ModelConfigDocument;
import com.tennis.prediction.repository.ModelConfigRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing prediction model configurations.
 */
@Service
public class ModelService {

    private final ModelConfigRepository modelConfigRepository;

    // Default model configurations (includes OAPS and OWL factors)
    private static final Map<String, ModelConfigDocument> BUILT_IN_MODELS;
    static {
        Map<String, ModelConfigDocument> models = new LinkedHashMap<>();
        
        // Legacy Balanced Model (without OWL) - for comparison
        models.put("balanced-legacy", createBuiltInModel("balanced-legacy", "Balanced (Legacy)", 
                "Original balanced model without OWL factors", createLegacyWeights(
                    0.18, 0.16, 0.16, 0.12, 0.08, 0.12, 0.18
                )));
        
        // ========== OWL-ENHANCED MODELS ==========
        
        // NEW DEFAULT: OWL Balanced - balanced approach with OWL factors
        models.put("balanced", createBuiltInModel("balanced", "OWL Balanced", 
                "Balanced model enhanced with OWL momentum and ratings", createWeights(
                    0.10, 0.14, 0.10, 0.10, 0.05, 0.05, 0.12, 0.18, 0.16
                )));
        
        // OWL Heavy - prioritizes OWL system
        models.put("owl-heavy", createBuiltInModel("owl-heavy", "OWL Heavy", 
                "Prioritizes OWL rating and momentum for predictions", createWeights(
                    0.08, 0.12, 0.05, 0.08, 0.04, 0.03, 0.10, 0.28, 0.22
                )));
        
        // OWL Momentum Hunter - focuses on hot/cold streaks
        models.put("owl-momentum", createBuiltInModel("owl-momentum", "OWL Momentum Hunter", 
                "Finds players with hot momentum trends", createWeights(
                    0.08, 0.10, 0.08, 0.08, 0.04, 0.05, 0.12, 0.15, 0.30
                )));
        
        // Ranking Heavy - prioritizes world ranking + OWL rating
        models.put("ranking-heavy", createBuiltInModel("ranking-heavy", "Ranking Heavy", 
                "Prioritizes world ranking and OWL rating", createWeights(
                    0.20, 0.10, 0.08, 0.10, 0.05, 0.05, 0.12, 0.20, 0.10
                )));
        
        // Form Focused - prioritizes recent performance + OWL momentum
        models.put("form-focused", createBuiltInModel("form-focused", "Form Focused", 
                "Prioritizes recent form and OWL momentum", createWeights(
                    0.08, 0.10, 0.18, 0.08, 0.05, 0.08, 0.15, 0.10, 0.18
                )));
        
        // H2H Focused - prioritizes head-to-head record
        models.put("h2h-focused", createBuiltInModel("h2h-focused", "H2H Focused", 
                "Prioritizes head-to-head record with OWL context", createWeights(
                    0.08, 0.28, 0.08, 0.10, 0.05, 0.05, 0.10, 0.14, 0.12
                )));
        
        // Surface Specialist - prioritizes surface performance
        models.put("surface-specialist", createBuiltInModel("surface-specialist", "Surface Specialist", 
                "Prioritizes surface performance with OWL", createWeights(
                    0.08, 0.10, 0.08, 0.25, 0.05, 0.05, 0.10, 0.15, 0.14
                )));
        
        // Value Hunter - heavily weights OAPS + OWL momentum
        models.put("value-hunter", createBuiltInModel("value-hunter", "Value Hunter", 
                "Finds players outperforming market expectations", createWeights(
                    0.05, 0.08, 0.10, 0.05, 0.04, 0.08, 0.28, 0.10, 0.22
                )));
        
        // Upset Finder - targets potential upsets using OWL momentum
        models.put("upset-finder", createBuiltInModel("upset-finder", "Upset Finder", 
                "Identifies underdogs with hot OWL momentum", createWeights(
                    0.05, 0.12, 0.05, 0.08, 0.05, 0.05, 0.20, 0.12, 0.28
                )));
        
        // ========== TOP PERFORMING CUSTOM MODELS (Backtested Winners) ==========
        
        // #1 Rating H2H Blend - 76.9% accuracy, +50.8% ROI
        models.put("rating-h2h-blend", createBuiltInModel("rating-h2h-blend", "⭐ Rating H2H Blend", 
                "TOP PERFORMER: Combined ratings (55%) + strong H2H (20%)", createWeights(
                    0.30, 0.20, 0.10, 0.00, 0.00, 0.00, 0.10, 0.25, 0.05
                )));
        
        // #2 Double Rating Plus - 73.1% accuracy, +44.2% ROI
        models.put("double-rating-plus", createBuiltInModel("double-rating-plus", "⭐ Double Rating Plus", 
                "TOP PERFORMER: Double Rating with boosted OAPS (20%)", createWeights(
                    0.28, 0.10, 0.10, 0.00, 0.02, 0.02, 0.20, 0.28, 0.00
                )));
        
        // #3 Ranking H2H Combo - 73.1% accuracy, +43.0% ROI
        models.put("ranking-h2h-combo", createBuiltInModel("ranking-h2h-combo", "⭐ Ranking H2H Combo", 
                "TOP PERFORMER: Ranking (35%) + H2H (25%)", createWeights(
                    0.35, 0.25, 0.15, 0.05, 0.03, 0.02, 0.05, 0.10, 0.00
                )));
        
        // #4 Double Rating - 69.2% accuracy, +37.2% ROI
        models.put("double-rating", createBuiltInModel("double-rating", "⭐ Double Rating", 
                "TOP PERFORMER: ATP Ranking (30%) + OWL Rating (30%)", createWeights(
                    0.30, 0.10, 0.10, 0.05, 0.03, 0.02, 0.10, 0.30, 0.00
                )));
        
        // #5 Ranking Form Hybrid - 69.2% accuracy, +37.2% ROI  
        models.put("ranking-form-hybrid", createBuiltInModel("ranking-form-hybrid", "⭐ Ranking Form Hybrid", 
                "TOP PERFORMER: Ranking (35%) + Recent Form (25%)", createWeights(
                    0.35, 0.10, 0.25, 0.05, 0.03, 0.02, 0.15, 0.05, 0.00
                )));
        
        // #6 Pure Ranking - 69.2% accuracy, +35.7% ROI
        models.put("pure-ranking", createBuiltInModel("pure-ranking", "⭐ Pure Ranking", 
                "TOP PERFORMER: Maximum ranking weight (50%)", createWeights(
                    0.50, 0.15, 0.15, 0.05, 0.03, 0.02, 0.05, 0.05, 0.00
                )));
        
        BUILT_IN_MODELS = Collections.unmodifiableMap(models);
    }
    
    /**
     * Create weights map with OWL factors (new models):
     * ranking, h2h, recentForm, surfaceForm, fatigue, momentum, oaps, owlRating, owlMomentum
     */
    private static Map<String, Double> createWeights(double ranking, double h2h, double recentForm, 
            double surfaceForm, double fatigue, double momentum, double oaps, 
            double owlRating, double owlMomentum) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ranking", ranking);
        weights.put("h2h", h2h);
        weights.put("recentForm", recentForm);
        weights.put("surfaceForm", surfaceForm);
        weights.put("fatigue", fatigue);
        weights.put("momentum", momentum);
        weights.put("oddsAdjustedPerformance", oaps);
        weights.put("owlRating", owlRating);
        weights.put("owlMomentum", owlMomentum);
        return weights;
    }
    
    /**
     * Create legacy weights map (without OWL):
     * ranking, h2h, recentForm, surfaceForm, fatigue, momentum, oaps
     */
    private static Map<String, Double> createLegacyWeights(double ranking, double h2h, double recentForm, 
            double surfaceForm, double fatigue, double momentum, double oaps) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ranking", ranking);
        weights.put("h2h", h2h);
        weights.put("recentForm", recentForm);
        weights.put("surfaceForm", surfaceForm);
        weights.put("fatigue", fatigue);
        weights.put("momentum", momentum);
        weights.put("oddsAdjustedPerformance", oaps);
        // OWL factors default to 0 weight for legacy models
        weights.put("owlRating", 0.0);
        weights.put("owlMomentum", 0.0);
        return weights;
    }

    public ModelService(ModelConfigRepository modelConfigRepository) {
        this.modelConfigRepository = modelConfigRepository;
    }

    /**
     * Get all available models (built-in + custom).
     */
    public List<ModelConfigDocument> getAllModels() {
        List<ModelConfigDocument> customModels = modelConfigRepository.findAll();
        
        // Add built-in models that aren't overridden
        for (ModelConfigDocument builtIn : BUILT_IN_MODELS.values()) {
            if (customModels.stream().noneMatch(m -> m.getModelId().equals(builtIn.getModelId()))) {
                customModels.add(builtIn);
            }
        }
        
        return customModels;
    }

    /**
     * Get a specific model by ID.
     */
    public Optional<ModelConfigDocument> getModel(String modelId) {
        // Check custom models first
        Optional<ModelConfigDocument> custom = modelConfigRepository.findByModelId(modelId);
        if (custom.isPresent()) {
            return custom;
        }
        
        // Fall back to built-in
        return Optional.ofNullable(BUILT_IN_MODELS.get(modelId));
    }

    /**
     * Get the active model (or default).
     */
    public ModelConfigDocument getActiveModel() {
        return modelConfigRepository.findFirstByActiveTrue()
                .orElse(BUILT_IN_MODELS.get("balanced"));
    }

    /**
     * Create or update a custom model.
     */
    public ModelConfigDocument saveModel(ModelConfigDocument model) {
        model.setUpdatedAt(Instant.now());
        
        // If setting as active, deactivate others
        if (model.isActive()) {
            modelConfigRepository.findByActive(true).forEach(m -> {
                if (!m.getModelId().equals(model.getModelId())) {
                    m.setActive(false);
                    modelConfigRepository.save(m);
                }
            });
        }
        
        return modelConfigRepository.save(model);
    }

    /**
     * Create a new custom model from weights.
     */
    public ModelConfigDocument createCustomModel(String modelId, String name, String description, Map<String, Double> weights) {
        if (BUILT_IN_MODELS.containsKey(modelId)) {
            throw new IllegalArgumentException("Cannot override built-in model: " + modelId);
        }

        ModelConfigDocument model = new ModelConfigDocument();
        model.setModelId(modelId);
        model.setName(name);
        model.setDescription(description);
        model.setVersion("1.0");
        model.setWeights(weights);
        model.setActive(false);

        return modelConfigRepository.save(model);
    }

    /**
     * Delete a custom model.
     */
    public boolean deleteModel(String modelId) {
        if (BUILT_IN_MODELS.containsKey(modelId)) {
            throw new IllegalArgumentException("Cannot delete built-in model: " + modelId);
        }

        Optional<ModelConfigDocument> model = modelConfigRepository.findByModelId(modelId);
        if (model.isPresent()) {
            modelConfigRepository.delete(model.get());
            return true;
        }
        return false;
    }

    /**
     * Set a model as active.
     */
    public ModelConfigDocument setActiveModel(String modelId) {
        ModelConfigDocument model = getModel(modelId)
                .orElseThrow(() -> new IllegalArgumentException("Model not found: " + modelId));

        // If built-in, save a copy as custom to track active state
        if (BUILT_IN_MODELS.containsKey(modelId)) {
            model = new ModelConfigDocument();
            ModelConfigDocument builtIn = BUILT_IN_MODELS.get(modelId);
            model.setModelId(builtIn.getModelId());
            model.setName(builtIn.getName());
            model.setDescription(builtIn.getDescription());
            model.setVersion(builtIn.getVersion());
            model.setWeights(builtIn.getWeights());
        }

        model.setActive(true);
        return saveModel(model);
    }

    private static ModelConfigDocument createBuiltInModel(String id, String name, String description, Map<String, Double> weights) {
        ModelConfigDocument model = new ModelConfigDocument();
        model.setModelId(id);
        model.setName(name);
        model.setDescription(description);
        model.setVersion("1.0");
        model.setWeights(weights);
        model.setActive("balanced".equals(id));
        return model;
    }
}

