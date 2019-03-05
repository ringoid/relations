package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserMessageEvent extends BaseEvent {
    private String messageId;
    private String conversationId;
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private String text;
    private long messageAt;
    private String source;
    private long unixTime;

    public UserMessageEvent botEvent() {
        UserMessageEvent botEvent = new UserMessageEvent();
        botEvent.setUserId(userId);
        botEvent.setOriginPhotoId(originPhotoId);
        botEvent.setTargetUserId(targetUserId);
        botEvent.setText(text);
        botEvent.setMessageAt(messageAt);
        botEvent.setSource(source);
        botEvent.setUnixTime(unixTime);
        botEvent.setEventType("BOT_" + eventType);

        return botEvent;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOriginPhotoId() {
        return originPhotoId;
    }

    public void setOriginPhotoId(String originPhotoId) {
        this.originPhotoId = originPhotoId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }

    public long getMessageAt() {
        return messageAt;
    }

    public void setMessageAt(long messageAt) {
        this.messageAt = messageAt;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String toString() {
        return "UserMessageEvent{" +
                "messageId='" + messageId + '\'' +
                ", conversationId='" + conversationId + '\'' +
                ", userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", text='" + text + '\'' +
                ", messageAt=" + messageAt +
                ", source='" + source + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
