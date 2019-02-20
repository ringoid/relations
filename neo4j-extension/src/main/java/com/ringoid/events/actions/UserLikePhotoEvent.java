package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserLikePhotoEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;
    private String targetUserId;
    private long likeCount;
    private long likedAt;
    private String source;
    private String internalServiceSource;
    private long unixTime;

    public UserLikePhotoEvent botUserLikePhotoEvent() {
        UserLikePhotoEvent botEvent = new UserLikePhotoEvent();
        botEvent.setUserId(userId);
        botEvent.setOriginPhotoId(originPhotoId);
        botEvent.setTargetUserId(targetUserId);
        botEvent.setLikeCount(likeCount);
        botEvent.setLikedAt(likedAt);
        botEvent.setSource(source);
        botEvent.setInternalServiceSource(internalServiceSource);
        botEvent.setUnixTime(unixTime);

        botEvent.setEventType("BOT_" + eventType);
        return botEvent;
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

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
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

    public long getLikedAt() {
        return likedAt;
    }

    public void setLikedAt(long likedAt) {
        this.likedAt = likedAt;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    @Override
    public String toString() {
        return "UserLikePhotoEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", likeCount=" + likeCount +
                ", likedAt=" + likedAt +
                ", source='" + source + '\'' +
                ", internalServiceSource='" + internalServiceSource + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
