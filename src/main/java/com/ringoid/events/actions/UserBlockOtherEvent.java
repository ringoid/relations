package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBlockOtherEvent extends BaseEvent {
    private String userId;
    private String targetUserId;
    private String targetPhotoId;
    private String originPhotoId;
    private long blockedAt;
    private long blockReasonNum;
    private String source;
    private String internalServiceSource;
    private long unixTime;

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

    public long getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(long blockedAt) {
        this.blockedAt = blockedAt;
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

    public long getBlockReasonNum() {
        return blockReasonNum;
    }

    public void setBlockReasonNum(long blockReasonNum) {
        this.blockReasonNum = blockReasonNum;
    }

    public String getTargetPhotoId() {
        return targetPhotoId;
    }

    public void setTargetPhotoId(String targetPhotoId) {
        this.targetPhotoId = targetPhotoId;
    }

    public String getOriginPhotoId() {
        return originPhotoId;
    }

    public void setOriginPhotoId(String originPhotoId) {
        this.originPhotoId = originPhotoId;
    }

    @Override
    public String toString() {
        return "UserBlockOtherEvent{" +
                "userId='" + userId + '\'' +
                ", targetUserId='" + targetUserId + '\'' +
                ", targetPhotoId='" + targetPhotoId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                ", blockedAt=" + blockedAt +
                ", blockReasonNum=" + blockReasonNum +
                ", source='" + source + '\'' +
                ", internalServiceSource='" + internalServiceSource + '\'' +
                ", unixTime=" + unixTime +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
