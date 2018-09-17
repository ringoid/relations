package com.ringoid;

public enum PersonProperties {
    USER_ID("user_id"),
    SEX("sex"),
    YEAR("year_of_birth"),
    CREATED("profile_created_at"),
    WHO_CAN_SEE_PHOTO("who_can_see_photo"),
    SAFE_DISTANCE_IN_METER("safe_distance_in_meter");

    private String propertyName;

    PersonProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
