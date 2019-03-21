package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class NewFacesResponse {
    private List<Profile> newFaces;
    private long lastActionTime;

    public NewFacesResponse() {
        this.newFaces = new ArrayList<>();
    }

    public List<Profile> getNewFaces() {
        return newFaces;
    }

    public void setNewFaces(List<Profile> newFaces) {
        this.newFaces = newFaces;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public void setLastActionTime(long lastActionTime) {
        this.lastActionTime = lastActionTime;
    }
}
