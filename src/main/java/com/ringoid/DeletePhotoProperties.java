package com.ringoid;

public enum DeletePhotoProperties {
    DELETE_AT("delete_at");

    private String propertyName;

    DeletePhotoProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
