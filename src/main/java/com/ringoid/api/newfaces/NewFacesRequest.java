package com.ringoid.api.newfaces;

public class NewFacesRequest {
    private String userId;
    private int limit;
    private long requestedLastActionTime;

    public long getRequestedLastActionTime() {
        return requestedLastActionTime;
    }

    public void setRequestedLastActionTime(long requestedLastActionTime) {
        this.requestedLastActionTime = requestedLastActionTime;
    }

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

    @Override
    public String toString() {
        return "NewFacesRequest{" +
                "userId='" + userId + '\'' +
                ", limit=" + limit +
                '}';
    }
}
