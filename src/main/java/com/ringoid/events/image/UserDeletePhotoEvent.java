package com.ringoid.events.image;

import com.ringoid.events.BaseEvent;

public class UserDeletePhotoEvent extends BaseEvent {
    private String userId;
    private String photoId;
    private int unixTime;
    private boolean userTakePartInReport;

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public boolean isUserTakePartInReport() {
        return userTakePartInReport;
    }

    public void setUserTakePartInReport(boolean userTakePartInReport) {
        this.userTakePartInReport = userTakePartInReport;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(int unixTime) {
        this.unixTime = unixTime;
    }

    @Override
    public String toString() {
        return "UserDeletePhotoEvent{" +
                "userId='" + userId + '\'' +
                ", photoId='" + photoId + '\'' +
                ", unixTime=" + unixTime +
                ", userTakePartInReport=" + userTakePartInReport +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
