package com.ringoid;

public enum HideProperties {
    HIDE_AT("hide_at"),
    HIDE_REASON("reason");

    private String propertyName;

    HideProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
