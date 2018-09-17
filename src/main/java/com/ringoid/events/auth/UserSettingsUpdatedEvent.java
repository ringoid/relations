package com.ringoid.events.auth;


import com.ringoid.events.BaseEvent;

public class UserSettingsUpdatedEvent extends BaseEvent {
    private String userId;
    private String whoCanSeePhoto;      //OPPOSITE (default) || INCOGNITO || ONLY_ME
    private int safeDistanceInMeter;    // 0 (default for men) || 10 (default for women)

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getWhoCanSeePhoto() {
        return whoCanSeePhoto;
    }

    public void setWhoCanSeePhoto(String whoCanSeePhoto) {
        this.whoCanSeePhoto = whoCanSeePhoto;
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
                ", whoCanSeePhoto='" + whoCanSeePhoto + '\'' +
                ", safeDistanceInMeter=" + safeDistanceInMeter +
                '}';
    }
}
