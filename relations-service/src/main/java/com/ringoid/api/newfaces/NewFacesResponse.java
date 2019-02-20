package com.ringoid.api.newfaces;

import com.ringoid.api.ProfileResponse;

import java.util.List;

public class NewFacesResponse {
    private List<ProfileResponse> newFaces;
    private long lastActionTime;

    public List<ProfileResponse> getNewFaces() {
        return newFaces;
    }

    public void setNewFaces(List<ProfileResponse> newFaces) {
        this.newFaces = newFaces;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
}