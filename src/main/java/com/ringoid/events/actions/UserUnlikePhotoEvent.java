package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUnlikePhotoEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private long unLikedAt;
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

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public long getUnLikedAt() {
        return unLikedAt;
    }

    public void setUnLikedAt(long unLikedAt) {
        this.unLikedAt = unLikedAt;
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

    @Override
    public String toString() {
        return "UserUnlikePhotoEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", unLikedAt=" + unLikedAt +
                ", source='" + source + '\'' +
                ", internalServiceSource='" + internalServiceSource + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
