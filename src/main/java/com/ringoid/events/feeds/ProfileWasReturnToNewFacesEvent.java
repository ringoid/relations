package com.ringoid.events.feeds;

import com.ringoid.events.BaseEvent;

import java.util.List;

public class ProfileWasReturnToNewFacesEvent extends BaseEvent {
    private String userId;
    private List<String> targetUserIds;
    private int timeToDelete;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getTargetUserIds() {
        return targetUserIds;
    }

    public void setTargetUserIds(List<String> targetUserIds) {
        this.targetUserIds = targetUserIds;
    }

    public int getTimeToDelete() {
        return timeToDelete;
    }

    public void setTimeToDelete(int timeToDelete) {
        this.timeToDelete = timeToDelete;
    }

    @Override
    public String toString() {
        return "ProfileWasReturnToNewFacesEvent{" +
                "userId='" + userId + '\'' +
                ", targetUserIds=" + targetUserIds +
                ", timeToDelete=" + timeToDelete +
                '}';
    }
}
