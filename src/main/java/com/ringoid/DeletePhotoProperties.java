package com.ringoid;

public enum DeletePhotoProperties {
    HIDE_AT("hide_at");

    private String propertyName;

    DeletePhotoProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
