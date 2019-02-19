package com.ringoid.graphaware;

import com.ringoid.events.BaseEvent;

import java.util.List;

public class EventContainer {
    private List<BaseEvent> events;

    public List<BaseEvent> getEvents() {
        return events;
    }

    public void setEvents(List<BaseEvent> events) {
        this.events = events;
    }

    @Override
    public String toString() {
        return "EventContainer{" +
                "events=" + events +
                '}';
    }
}
