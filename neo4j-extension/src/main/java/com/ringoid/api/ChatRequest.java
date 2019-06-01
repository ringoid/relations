package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatRequest {
    private String userId;
    private String oppositeUserId;
    private long requestedLastActionTime;
    private String resolution;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOppositeUserId() {
        return oppositeUserId;
    }

    public void setOppositeUserId(String oppositeUserId) {
        this.oppositeUserId = oppositeUserId;
    }

    public long getRequestedLastActionTime() {
        return requestedLastActionTime;
    }

    public void setRequestedLastActionTime(long requestedLastActionTime) {
        this.requestedLastActionTime = requestedLastActionTime;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
