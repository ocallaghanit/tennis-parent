package com.tennis.adapter.model;

import org.bson.Document;
import org.springframework.data.annotation.Id;

import java.time.Instant;

/**
 * Base class for all stored documents.
 * Stores the raw API response as a MongoDB Document (BSON).
 */
public abstract class BaseDocument {

    @Id
    private String id;

    /**
     * The raw JSON response from API Tennis, stored as BSON Document
     */
    private Document raw;

    /**
     * When this document was fetched from the API
     */
    private Instant fetchedAt;

    /**
     * When this document was last updated
     */
    private Instant updatedAt;

    public BaseDocument() {
        this.fetchedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Document getRaw() {
        return raw;
    }

    public void setRaw(Document raw) {
        this.raw = raw;
        this.updatedAt = Instant.now();
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

