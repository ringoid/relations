package com.ringoid.events;

public class BaseEvent {
    private String eventType;

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
