package com.ringoid;

public enum BlockProperties {
    BLOCK_AT("view_at");

    private String propertyName;

    BlockProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
