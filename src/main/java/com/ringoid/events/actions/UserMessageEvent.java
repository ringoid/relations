package com.ringoid.events.actions;

import com.ringoid.events.BaseEvent;

public class UserMessageEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private String text;
    private int messageAt;
    private String source;
    private int unixTime;

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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "UserMessageEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", text='" + text + '\'' +
                ", messageAt=" + messageAt +
                ", source='" + source + '\'' +
                ", unixTime=" + unixTime +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
