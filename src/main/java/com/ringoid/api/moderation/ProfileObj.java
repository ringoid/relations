package com.ringoid.api.moderation;

import java.util.List;

public class ProfileObj {
    private String userId;
    private List<PhotoObj> photos;

    public ProfileObj() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<PhotoObj> getPhotos() {
        return photos;
    }

    public void setPhotos(List<PhotoObj> photos) {
        this.photos = photos;
    }

    @Override
    public String toString() {
        return "ProfileObj{" +
                "userId='" + userId + '\'' +
                ", photos=" + photos +
                '}';
    }
}
