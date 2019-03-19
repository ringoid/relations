package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LMMRequest {
    private String userId;
    private boolean requestNewPart;
    private long requestedLastActionTime;
    private String resolution;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isRequestNewPart() {
        return requestNewPart;
    }

    public void setRequestNewPart(boolean requestNewPart) {
        this.requestNewPart = requestNewPart;
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
