package com.ringoid.events.actions;

import com.ringoid.events.BaseEvent;

public class UserBlockOtherEvent extends BaseEvent {
    private String userId;
    private String targetUserId;
    private String targetPhotoId;
    private String originPhotoId;
    private int blockedAt;
    private int blockReasonNum;
    private String source;
    private String internalServiceSource;
    private int unixTime;

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

    public int getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(int blockedAt) {
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

    public int getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(int unixTime) {
        this.unixTime = unixTime;
    }

    public int getBlockReasonNum() {
        return blockReasonNum;
    }

    public void setBlockReasonNum(int blockReasonNum) {
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
