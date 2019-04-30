package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LMHISRequest {
    private String userId;
    private long requestedLastActionTime;
    private boolean requestNewPart;
    private String resolution;
    private String lmhisPart;//hellos | inbox | sent

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public String getLmhisPart() {
        return lmhisPart;
    }

    public void setLmhisPart(String lmhisPart) {
        this.lmhisPart = lmhisPart;
    }

    public boolean isRequestNewPart() {
        return requestNewPart;
    }

    public void setRequestNewPart(boolean requestNewPart) {
        this.requestNewPart = requestNewPart;
    }
}
