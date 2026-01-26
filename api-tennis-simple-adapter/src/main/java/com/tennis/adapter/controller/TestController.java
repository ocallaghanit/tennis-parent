package com.tennis.adapter.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.tennis.adapter.client.ApiTennisClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test endpoints to verify API Tennis connectivity.
 */
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test", description = "Test API Tennis connectivity")
public class TestController {

    private final ApiTennisClient apiClient;

    public TestController(ApiTennisClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping
    @Operation(summary = "Test connection", description = "Verify API Tennis connectivity by fetching event types")
    public ResponseEntity<Map<String, Object>> testConnection() {
        try {
            JsonNode response = apiClient.testConnection();
            boolean success = response != null && 
                             response.has("success") && 
                             response.get("success").asInt() == 1;
            
            int resultCount = 0;
            if (success && response.has("result") && response.get("result").isArray()) {
                resultCount = response.get("result").size();
            }
            
            return ResponseEntity.ok(Map.of(
                    "status", success ? "connected" : "error",
                    "apiSuccess", success,
                    "eventTypesFound", resultCount,
                    "message", success 
                            ? "Successfully connected to API Tennis" 
                            : "Connection failed - check API key"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "error",
                    "apiSuccess", false,
                    "message", "Connection failed: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/raw")
    @Operation(summary = "Raw API call", description = "Make a raw call to API Tennis for debugging")
    public ResponseEntity<JsonNode> rawApiCall(
            @Parameter(description = "API method (e.g., get_events, get_fixtures)", example = "get_events")
            @RequestParam String method,
            @Parameter(description = "Additional parameters as query string")
            @RequestParam(required = false) Map<String, String> params
    ) {
        // Remove the 'method' param from the map since it's handled separately
        if (params != null) {
            params.remove("method");
        }
        JsonNode response = apiClient.callApi(method, params);
        return ResponseEntity.ok(response);
    }
}
