package com.ringoid.events.actions;

import com.ringoid.events.BaseEvent;

public class UserViewPhotoEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private long viewCount;
    private long viewTimeMillis;
    private long viewAt;
    private String source;
    private String internalServiceSource;
    private long unixTime;

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

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getViewTimeMillis() {
        return viewTimeMillis;
    }

    public void setViewTimeMillis(long viewTimeMillis) {
        this.viewTimeMillis = viewTimeMillis;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getInternalServiceSource() {
        return internalServiceSource;
    }

    public void setInternalServiceSource(String internalServiceSource) {
        this.internalServiceSource = internalServiceSource;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }

    public long getViewAt() {
        return viewAt;
    }

    public void setViewAt(long viewAt) {
        this.viewAt = viewAt;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    @Override
    public String toString() {
        return "UserViewPhotoEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", viewCount=" + viewCount +
                ", viewTimeMillis=" + viewTimeMillis +
                ", viewAt=" + viewAt +
                ", source='" + source + '\'' +
                ", internalServiceSource='" + internalServiceSource + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
