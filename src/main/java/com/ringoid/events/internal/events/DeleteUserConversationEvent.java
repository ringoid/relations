package com.ringoid.events.internal.events;

import com.ringoid.events.BaseEvent;

import static com.ringoid.events.EventTypes.INTERNAL_DELETE_USER_CONVERSATION_EVENT;

public class DeleteUserConversationEvent extends BaseEvent {
    private String userId;
    private String targetUserId;

    public DeleteUserConversationEvent() {
    }

    public DeleteUserConversationEvent(String userId, String targetUserId) {
        this.userId = userId;
        this.targetUserId = targetUserId;
        this.eventType = INTERNAL_DELETE_USER_CONVERSATION_EVENT.name();
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
}
