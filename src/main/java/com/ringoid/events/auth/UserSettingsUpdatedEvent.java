package com.ringoid.events.auth;


import com.ringoid.events.BaseEvent;

public class UserSettingsUpdatedEvent extends BaseEvent {
    private String userId;
    private int safeDistanceInMeter;    // 0 (default for men) || 10 (default for women)

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getSafeDistanceInMeter() {
        return safeDistanceInMeter;
    }

    public void setSafeDistanceInMeter(int safeDistanceInMeter) {
        this.safeDistanceInMeter = safeDistanceInMeter;
    }

    @Override
    public String toString() {
        return "UserSettingsUpdatedEvent{" +
                "userId='" + userId + '\'' +
                ", safeDistanceInMeter=" + safeDistanceInMeter +
                '}';
    }
}
