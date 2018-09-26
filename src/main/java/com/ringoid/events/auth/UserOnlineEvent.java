package com.ringoid.events.auth;

import com.ringoid.events.BaseEvent;

public class UserOnlineEvent extends BaseEvent {
    private String userId;
    private int unixTime;

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
        return "UserOnlineEvent{" +
                "userId='" + userId + '\'' +
                ", unixTime=" + unixTime +
                '}';
    }
}
