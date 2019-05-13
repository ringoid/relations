package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Photo {
    private String originPhotoId;
    private String resizedPhotoId;
    private String link;
    private String resolution;
    private String thumbnailLink;

    public String getOriginPhotoId() {
        return originPhotoId;
    }

    public void setOriginPhotoId(String originPhotoId) {
        this.originPhotoId = originPhotoId;
    }

    public String getResizedPhotoId() {
        return resizedPhotoId;
    }

    public void setResizedPhotoId(String resizedPhotoId) {
        this.resizedPhotoId = resizedPhotoId;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public String getThumbnailLink() {
        return thumbnailLink;
    }

    public void setThumbnailLink(String thumbnailLink) {
        this.thumbnailLink = thumbnailLink;
    }
}
