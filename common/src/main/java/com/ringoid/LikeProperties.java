package com.ringoid;

public enum LikeProperties {
    LIKE_COUNT("like_count");

    private String propertyName;

    LikeProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
