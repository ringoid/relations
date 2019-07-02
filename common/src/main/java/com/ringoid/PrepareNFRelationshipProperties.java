package com.ringoid;

public enum PrepareNFRelationshipProperties {

    INDEX("index");

    private String propertyName;

    PrepareNFRelationshipProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
