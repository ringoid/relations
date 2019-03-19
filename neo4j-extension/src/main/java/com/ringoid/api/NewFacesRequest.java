package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewFacesRequest {
    private String userId;
    private int limit;
    private long requestedLastActionTime;
    private String resolution;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
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
