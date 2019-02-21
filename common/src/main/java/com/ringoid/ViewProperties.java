package com.ringoid;

public enum ViewProperties {
    VIEW_COUNT("view_count"),
    VIEW_TIME("view_time"),
    VIEW_AT("view_at");

    private String propertyName;

    ViewProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
