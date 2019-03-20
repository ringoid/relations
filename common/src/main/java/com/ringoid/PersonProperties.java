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
    REFERRAL_ID("referral_id");

    private String propertyName;

    PersonProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
