package com.ringoid;

public enum ConversationProperties {
    CONVERSATION_ID("conversation_id"),
    USER_ONE("user_one"),
    USER_TWO("user_two"),
    STARTED_AT("started_at");

    private String propertyName;

    ConversationProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

}
