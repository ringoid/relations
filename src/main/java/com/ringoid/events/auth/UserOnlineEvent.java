package com.ringoid.events.auth;

import com.ringoid.events.BaseEvent;

public class UserOnlineEvent extends BaseEvent {
    private String userId;
    private long unixTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
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
