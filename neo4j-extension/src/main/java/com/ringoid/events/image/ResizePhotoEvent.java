package com.ringoid.events.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ResizePhotoEvent extends BaseEvent {
    private String userId;
    private String photoId;
    private String resizedPhotoId;
    private String resizedResolution;
    private String resizedPhotoLink;
    private long unixTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public String getResizedPhotoId() {
        return resizedPhotoId;
    }

    public void setResizedPhotoId(String resizedPhotoId) {
        this.resizedPhotoId = resizedPhotoId;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }

    public String getResizedResolution() {
        return resizedResolution;
    }

    public void setResizedResolution(String resizedResolution) {
        this.resizedResolution = resizedResolution;
    }

    public String getResizedPhotoLink() {
        return resizedPhotoLink;
    }

    public void setResizedPhotoLink(String resizedPhotoLink) {
        this.resizedPhotoLink = resizedPhotoLink;
    }
}
