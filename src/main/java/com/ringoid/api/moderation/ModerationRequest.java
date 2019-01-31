package com.ringoid.api.moderation;

import java.util.Map;

public class ModerationRequest {
    private String queryType;
    private int limit;
    private Map<String, String> profilePhotoMap;

    public ModerationRequest() {
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public Map<String, String> getProfilePhotoMap() {
        return profilePhotoMap;
    }

    public void setProfilePhotoMap(Map<String, String> profilePhotoMap) {
        this.profilePhotoMap = profilePhotoMap;
    }

    @Override
    public String toString() {
        return "ModerationRequest{" +
                "queryType='" + queryType + '\'' +
                ", limit=" + limit +
                ", profilePhotoMap=" + profilePhotoMap +
                '}';
    }
}
