package com.ringoid.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCallDeleteHimselfEvent extends BaseEvent {
    private String userId;
    private long unixTime;
    private String userReportStatus;// CLEAN || TAKE_PART_IN_REPORT

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }

    public String getUserReportStatus() {
        return userReportStatus;
    }

    public void setUserReportStatus(String userReportStatus) {
        this.userReportStatus = userReportStatus;
    }

    @Override
    public String toString() {
        return "UserCallDeleteHimselfEvent{" +
                "userId='" + userId + '\'' +
                ", unixTime=" + unixTime +
                ", userReportStatus='" + userReportStatus + '\'' +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}
