package com.ringoid;

public enum BlockProperties {
    BLOCK_REASON_NUM("reason");

    private String propertyName;

    BlockProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
