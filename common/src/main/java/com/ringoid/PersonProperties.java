package com.ringoid;

public enum PersonProperties {
    USER_ID("user_id"),
    SEX("sex"),
    YEAR("year_of_birth"),
    CREATED("profile_created_at"),
    SAFE_DISTANCE_IN_METER("safe_distance_in_meter"),
    LAST_ONLINE_TIME("last_online_time"),
    LAST_ACTION_TIME("last_action_time"),
    LIKE_COUNTER("like_counter"),
    NEED_TO_MODERATE("need_to_moderate"),
    MODERATION_STARTED_AT("moderation_started_at"),
    REFERRAL_ID("referral_id"),
    PRIVATE_KEY("private_key"),
    SETTINGS_LOCALE("settings_locale"),
    SETTINGS_PUSH("settings_push"),
    SETTINGS_NEW_LIKE_PUSH("settings_new_like_push"),
    SETTINGS_NEW_MATCH_PUSH("settings_new_match_push"),
    SETTINGS_NEW_MESSAGE_PUSH("settings_new_message_push"),
    SETTINGS_TIMEZONE("settings_timezone"),
    PUSH_WAS_SENT_AT("push_was_sent_at"),
    LOCATION("location"),
    PROPERTY("property"),
    TRANSPORT("transport"),
    INCOME("income"),
    HEIGHT("height");

    private String propertyName;

    PersonProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
