package com.ringoid.api;

import java.util.List;

public class LMMResponse {
    private List<ProfileResponse> profiles;
    private long lastActionTime;

    public List<ProfileResponse> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileResponse> profiles) {
        this.profiles = profiles;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
}
