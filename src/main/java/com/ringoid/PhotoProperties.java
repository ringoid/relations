package com.ringoid;

public enum PhotoProperties {
    PHOTO_ID("photo_id"),
    PHOTO_UPLOADED("photo_uploaded_at"),
    NEED_TO_MODERATE("need_to_moderate");

    private String propertyName;

    PhotoProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
