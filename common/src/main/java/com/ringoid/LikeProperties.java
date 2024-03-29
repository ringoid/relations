package com.ringoid;

public enum LikeProperties {
    LIKE_COUNT("like_count"),
    LIKE_AT("like_at");

    private String propertyName;

    LikeProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
