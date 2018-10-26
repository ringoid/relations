package com.ringoid.api;

public class NewFacesRequest {
    private String userId;
    private String targetSex;
    private int lastTimeWasOnline;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTargetSex() {
        return targetSex;
    }

    public void setTargetSex(String targetSex) {
        this.targetSex = targetSex;
    }

    public int getLastTimeWasOnline() {
        return lastTimeWasOnline;
    }

    public void setLastTimeWasOnline(int lastTimeWasOnline) {
        this.lastTimeWasOnline = lastTimeWasOnline;
    }
}
