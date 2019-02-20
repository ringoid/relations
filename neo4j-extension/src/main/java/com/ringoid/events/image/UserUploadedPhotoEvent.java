package com.ringoid.events.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserUploadedPhotoEvent extends BaseEvent {
    private String userId;
    private String photoId;
    private String photoKey;
    private long unixTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    @Override
    public String toString() {
        return "UserUploadedPhotoEvent{" +
                "userId='" + userId + '\'' +
                ", photoId='" + photoId + '\'' +
                ", photoKey='" + photoKey + '\'' +
                ", unixTime=" + unixTime +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
