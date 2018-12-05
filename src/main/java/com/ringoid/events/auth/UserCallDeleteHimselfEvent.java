package com.ringoid.events.auth;

import com.ringoid.events.BaseEvent;

public class UserCallDeleteHimselfEvent extends BaseEvent {
    private String userId;
    private int unixTime;
    private String userReportStatus;// CLEAN || TAKE_PART_IN_REPORT

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(int unixTime) {
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
