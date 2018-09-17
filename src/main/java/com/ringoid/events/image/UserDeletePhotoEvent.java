package com.ringoid.events.image;

import com.ringoid.events.BaseEvent;

public class UserDeletePhotoEvent extends BaseEvent {
    private String photoId;

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    @Override
    public String toString() {
        return "UserDeletePhotoEvent{" +
                ", photoId='" + photoId + '\'' +
                '}';
    }
}
