package com.ringoid.api;

import java.util.List;

public class NewFacesResponse {
    private List<ProfileResponse> newFaces;

    public List<ProfileResponse> getNewFaces() {
        return newFaces;
    }

    public void setNewFaces(List<ProfileResponse> newFaces) {
        this.newFaces = newFaces;
    }
}