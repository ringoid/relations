package com.ringoid;

public enum MessageRelationshipProperties {
    MSG_COUNT("msg_count"),
    MSG_AT("msg_at");

    private String propertyName;

    MessageRelationshipProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
