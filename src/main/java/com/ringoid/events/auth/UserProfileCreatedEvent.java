package com.ringoid.events.auth;

import com.ringoid.events.BaseEvent;

public class UserProfileCreatedEvent extends BaseEvent {
    private String userId;
    private String sex;
    private int yearOfBirth;
    private int unixTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public int getYearOfBirth() {
        return yearOfBirth;
    }

    public void setYearOfBirth(int yearOfBirth) {
        this.yearOfBirth = yearOfBirth;
    }

    public int getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(int unixTime) {
        this.unixTime = unixTime;
    }

    @Override
    public String toString() {
        return "UserProfileCreatedEvent{" +
                "userId='" + userId + '\'' +
                ", sex='" + sex + '\'' +
                ", yearOfBirth=" + yearOfBirth +
                ", unixTime=" + unixTime +
                '}';
    }
}
