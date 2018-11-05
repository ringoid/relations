package com.ringoid;

public enum WasReturnedToNewFacesProperties {
    TIME_TO_DEL("delete_at");

    private String propertyName;

    WasReturnedToNewFacesProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
