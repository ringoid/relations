package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LCResponse {
    private List<Profile> profiles;
    private int allProfilesNum;
    private long lastActionTime;

    public LCResponse() {
        profiles = new ArrayList<>();
    }

    public List<Profile> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles;
    }

    public int getAllProfilesNum() {
        return allProfilesNum;
    }

    public void setAllProfilesNum(int allProfilesNum) {
        this.allProfilesNum = allProfilesNum;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
}
