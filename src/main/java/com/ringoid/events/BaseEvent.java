package com.ringoid.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties
public class BaseEvent {
    protected String eventType;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String toString() {
        return "BaseEvent{" +
                "eventType='" + eventType + '\'' +
                '}';
    }
}
