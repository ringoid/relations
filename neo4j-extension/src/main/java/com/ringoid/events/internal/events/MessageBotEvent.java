package com.ringoid.events.internal.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

import static com.ringoid.events.EventTypes.INTERNAL_USER_SEND_MESSAGE_EVENT;

@JsonIgnoreProperties
public class MessageBotEvent extends BaseEvent {
    private String userId;
    private String targetUserId;
    private String text;

    public MessageBotEvent() {
    }

    public MessageBotEvent(String userId, String targetUserId, String text) {
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.text = text;
        this.eventType = INTERNAL_USER_SEND_MESSAGE_EVENT.name();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

}
