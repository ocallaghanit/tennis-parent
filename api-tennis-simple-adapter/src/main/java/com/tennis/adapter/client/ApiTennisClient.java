package com.tennis.adapter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tennis.adapter.config.ApiTennisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.util.Map;

/**
 * Client for the API Tennis service.
 * All methods return raw JSON (JsonNode) which is stored directly in MongoDB.
 */
@Component
public class ApiTennisClient {

    private static final Logger log = LoggerFactory.getLogger(ApiTennisClient.class);

    private final WebClient webClient;
    private final ApiTennisProperties properties;
    private final ObjectMapper objectMapper;

    public ApiTennisClient(WebClient apiTennisWebClient, ApiTennisProperties properties, ObjectMapper objectMapper) {
        this.webClient = apiTennisWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Test connection to API Tennis
     */
    public JsonNode testConnection() {
        return callApi("get_events", Map.of());
    }

    /**
     * Get all event types (ATP Singles, WTA Singles, etc.)
     */
    public JsonNode getEvents() {
        return callApi("get_events", Map.of());
    }

    /**
     * Get tournaments, optionally filtered by event type
     */
    public JsonNode getTournaments(String eventTypeId) {
        if (eventTypeId != null && !eventTypeId.isBlank()) {
            return callApi("get_tournaments", Map.of("event_type_id", eventTypeId));
        }
        return callApi("get_tournaments", Map.of());
    }

    /**
     * Get fixtures (matches) with various filters
     */
    public JsonNode getFixtures(Map<String, String> params) {
        return callApi("get_fixtures", params);
    }

    /**
     * Get fixtures by date range
     */
    public JsonNode getFixturesByDateRange(String dateStart, String dateStop) {
        return callApi("get_fixtures", Map.of(
                "date_start", dateStart,
                "date_stop", dateStop
        ));
    }

    /**
     * Get fixtures by tournament
     */
    public JsonNode getFixturesByTournament(String tournamentId) {
        return callApi("get_fixtures", Map.of("tournament_key", tournamentId));
    }

    /**
     * Get player info
     */
    public JsonNode getPlayer(String playerId) {
        return callApi("get_players", Map.of("player_key", playerId));
    }

    /**
     * Get odds for a match
     */
    public JsonNode getOdds(String matchKey) {
        return callApi("get_odds", Map.of("match_key", matchKey));
    }

    /**
     * Get odds by date range (batch) - filters by ATP Singles event type
     */
    public JsonNode getOddsByDateRange(String dateStart, String dateStop) {
        return getOddsByDateRange(dateStart, dateStop, "265"); // Default to ATP Singles
    }

    /**
     * Get odds by date range with optional event type filter
     */
    public JsonNode getOddsByDateRange(String dateStart, String dateStop, String eventTypeKey) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("date_start", dateStart);
        params.put("date_stop", dateStop);
        if (eventTypeKey != null && !eventTypeKey.isBlank()) {
            params.put("event_type_key", eventTypeKey);
        }
        return callApi("get_odds", params);
    }

    /**
     * Get standings/rankings
     */
    public JsonNode getStandings(String eventTypeId) {
        return callApi("get_standings", Map.of("event_type_id", eventTypeId));
    }

    /**
     * Get head-to-head data between two players
     */
    public JsonNode getH2H(String firstPlayerId, String secondPlayerId) {
        return callApi("get_H2H", Map.of(
                "first_player_key", firstPlayerId,
                "second_player_key", secondPlayerId
        ));
    }

    /**
     * Generic API call method.
     * Throws WebClientResponseException for HTTP errors (4xx, 5xx).
     */
    public JsonNode callApi(String method, Map<String, String> params) {
        log.debug("Calling API Tennis: method={}, params={}", method, params);

        try {
            String response = webClient.get()
                    .uri(builder -> {
                        UriBuilder b = builder.path("/tennis/")
                                .queryParam("method", method)
                                .queryParam("APIkey", properties.getKey());
                        
                        if (params != null) {
                            for (Map.Entry<String, String> entry : params.entrySet()) {
                                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                                    b.queryParam(entry.getKey(), entry.getValue());
                                }
                            }
                        }
                        return b.build();
                    })
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode result = objectMapper.readTree(response);
            log.debug("API Tennis response received: success={}", 
                    result.has("success") ? result.get("success").asInt() : "unknown");
            
            return result;
        } catch (WebClientResponseException e) {
            // Re-throw HTTP errors directly so they can be handled properly
            log.error("API Tennis HTTP error: method={}, status={}, error={}", 
                    method, e.getStatusCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error calling API Tennis: method={}, error={}", method, e.getMessage());
            throw new ApiTennisException("Failed to call API Tennis: " + e.getMessage(), e);
        }
    }

    /**
     * Custom exception for API Tennis errors that are not HTTP errors
     */
    public static class ApiTennisException extends RuntimeException {
        public ApiTennisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
