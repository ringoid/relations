package com.ringoid.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileCreatedEvent extends BaseEvent {
    private String userId;
    private String sex;
    private int yearOfBirth;
    private long unixTime;

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

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
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