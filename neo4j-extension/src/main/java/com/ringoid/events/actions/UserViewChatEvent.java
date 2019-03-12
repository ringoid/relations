package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserViewChatEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private long viewTimeMillis;
    private long viewAt;

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

    public long getViewTimeMillis() {
        return viewTimeMillis;
    }

    public void setViewTimeMillis(long viewTimeMillis) {
        this.viewTimeMillis = viewTimeMillis;
    }

    public long getViewAt() {
        return viewAt;
    }

    public void setViewAt(long viewAt) {
        this.viewAt = viewAt;
    }

    @Override
    public String toString() {
        return "UserViewChatEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", viewTimeMillis=" + viewTimeMillis +
                ", viewAt=" + viewAt +
                '}';
    }
}
