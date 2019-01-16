package com.ringoid.api;

public class LMMRequest {
    private String userId;
    private boolean requestNewPart;
    private long requestedLastActionTime;

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

    @Override
    public String toString() {
        return "LMMRequest{" +
                "userId='" + userId + '\'' +
                ", requestNewPart=" + requestNewPart +
                ", requestedLastActionTime=" + requestedLastActionTime +
                '}';
    }
}
