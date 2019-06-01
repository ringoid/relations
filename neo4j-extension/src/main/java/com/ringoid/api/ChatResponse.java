package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatResponse {
    private Profile profile;
    private long lastActionTime;
    private boolean chatExists;

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }

    public boolean getChatExists() {
        return chatExists;
    }

    public void setChatExists(boolean chatExists) {
        this.chatExists = chatExists;
    }
}
