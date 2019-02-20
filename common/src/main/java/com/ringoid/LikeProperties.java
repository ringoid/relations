package com.ringoid;

public enum LikeProperties {

    LIKED_AT("liked_at"),
    LIKE_COUNT("like_count");

    private String propertyName;

    LikeProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
