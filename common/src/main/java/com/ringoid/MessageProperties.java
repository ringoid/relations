package com.ringoid;

public enum MessageProperties {
    MSG_ID("msg_id"),
    CLIENT_MSG_ID("client_msg_id"),
    MSG_SOURCE_USER_ID("msg_source_user_id"),
    MSG_TARGET_USER_ID("msg_target_user_id"),
    MSG_TEXT("msg_text"),
    MSG_PHOTO_ID("msg_photo_id"),
    MSG_AT("msg_at");

    private String propertyName;

    MessageProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }

}
