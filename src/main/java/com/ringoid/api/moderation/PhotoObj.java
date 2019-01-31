package com.ringoid.api.moderation;

public class PhotoObj {
    private String photoId;
    private boolean photoHidden;
    private boolean photoReported;
    private boolean wasModeratedBefore;
    private int likes;
    private long updatedAt;

    public PhotoObj() {
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public boolean isPhotoHidden() {
        return photoHidden;
    }

    public void setPhotoHidden(boolean photoHidden) {
        this.photoHidden = photoHidden;
    }

    public boolean isPhotoReported() {
        return photoReported;
    }

    public void setPhotoReported(boolean photoReported) {
        this.photoReported = photoReported;
    }

    public boolean isWasModeratedBefore() {
        return wasModeratedBefore;
    }

    public void setWasModeratedBefore(boolean wasModeratedBefore) {
        this.wasModeratedBefore = wasModeratedBefore;
    }

    public int getLikes() {
        return likes;
    }

    public void setLikes(int likes) {
        this.likes = likes;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "PhotoObj{" +
                "photoId='" + photoId + '\'' +
                ", photoHidden=" + photoHidden +
                ", photoReported=" + photoReported +
                ", wasModeratedBefore=" + wasModeratedBefore +
                ", likes=" + likes +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
