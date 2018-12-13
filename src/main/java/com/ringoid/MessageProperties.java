package com.ringoid;

public enum MessageProperties {
    MSG_COUNT("msg_count");

    private String propertyName;

    MessageProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
