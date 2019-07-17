package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscoverRequest {
    private String userId;
    private String accessToken;
    private String resolution;
    private long lastActionTime;
    private int limit;
    private Filter filter;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "DiscoverRequest{" +
                "userId='" + userId + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", resolution='" + resolution + '\'' +
                ", lastActionTime=" + lastActionTime +
                ", limit=" + limit +
                ", filter=" + filter +
                '}';
    }
}
