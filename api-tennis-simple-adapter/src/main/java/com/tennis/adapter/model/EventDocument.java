package com.tennis.adapter.model;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Stores event types (ATP Singles, WTA Singles, etc.)
 */
@Document(collection = "events")
public class EventDocument extends BaseDocument {

    private String eventKey;
    private String eventName;

    public EventDocument() {
        super();
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }
}

