package com.ringoid.events.internal.events;

import com.ringoid.events.BaseEvent;

import static com.ringoid.events.EventTypes.INTERNAL_USER_SEND_MESSAGE_EVENT;

public class MessageEvent extends BaseEvent {
    private String userId;
    private String targetUserId;
    private String text;
    private int unixTime;
    private int messageAt;

    public MessageEvent() {
    }

    public MessageEvent(String userId, String targetUserId, String text, int unixTime, int messageAt) {
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.text = text;
        this.unixTime = unixTime;
        this.messageAt = messageAt;
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

    public int getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(int unixTime) {
        this.unixTime = unixTime;
    }

    public int getMessageAt() {
        return messageAt;
    }

    public void setMessageAt(int messageAt) {
        this.messageAt = messageAt;
    }
}
