package com.ringoid.events.auth;

import com.ringoid.events.BaseEvent;

public class UserCallDeleteHimselfEvent extends BaseEvent {
    private String userId;
    private int unixTime;
    private boolean userWasReported;

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

    public boolean isUserWasReported() {
        return userWasReported;
    }

    public void setUserWasReported(boolean userWasReported) {
        this.userWasReported = userWasReported;
    }

    @Override
    public String toString() {
        return "UserCallDeleteHimselfEvent{" +
                "userId='" + userId + '\'' +
                ", unixTime=" + unixTime +
                ", userWasReported=" + userWasReported +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
