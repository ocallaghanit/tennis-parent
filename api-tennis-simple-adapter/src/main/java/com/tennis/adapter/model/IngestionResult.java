package com.tennis.adapter.model;

/**
 * Result object for ingestion operations.
 * Encapsulates success/failure state with error details.
 */
public class IngestionResult {

    private final boolean success;
    private final int count;
    private final String message;
    private final String errorType;

    private IngestionResult(boolean success, int count, String message, String errorType) {
        this.success = success;
        this.count = count;
        this.message = message;
        this.errorType = errorType;
    }

    public static IngestionResult success(int count, String message) {
        return new IngestionResult(true, count, message, null);
    }

    public static IngestionResult success(int count) {
        return new IngestionResult(true, count, "Ingested " + count + " records", null);
    }

    public static IngestionResult partialSuccess(int count, String message) {
        return new IngestionResult(true, count, message, "PARTIAL_SUCCESS");
    }

    public static IngestionResult failure(String message, String errorType) {
        return new IngestionResult(false, 0, message, errorType);
    }

    public static IngestionResult failure(String message) {
        return new IngestionResult(false, 0, message, "UNKNOWN");
    }

    public static IngestionResult apiError(String message) {
        return new IngestionResult(false, 0, message, "API_ERROR");
    }

    public static IngestionResult apiError(int statusCode, String details) {
        String msg = "API Tennis returned " + statusCode + " error";
        if (details != null && !details.isEmpty()) {
            msg += ": " + details;
        }
        return new IngestionResult(false, 0, msg, "API_ERROR_" + statusCode);
    }

    public boolean isSuccess() {
        return success;
    }

    public int getCount() {
        return count;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorType() {
        return errorType;
    }
}

