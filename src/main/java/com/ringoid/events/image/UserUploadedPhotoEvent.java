package com.ringoid.events.image;

import com.ringoid.events.BaseEvent;

public class UserUploadedPhotoEvent extends BaseEvent {
    private String userId;
    private String photoId;
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

    @Override
    public String toString() {
        return "UserUploadedPhotoEvent{" +
                "userId='" + userId + '\'' +
                ", photoId='" + photoId + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
