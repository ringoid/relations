package com.ringoid.events.internal.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PushObjectEvent extends BaseEvent {
    private String userId;
    private String sex;
    private String locale;
    private long lastOnlineTime;
    private long newMessageCount;
    private long newMatchCount;
    private long newLikeCount;
    private long newProfiles;
    private String pushType;
    private boolean newLikeEnabled;
    private boolean newMatchEnabled;
    private boolean newMessageEnabled;
    private String oppositeUserId;

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

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public long getLastOnlineTime() {
        return lastOnlineTime;
    }

    public void setLastOnlineTime(long lastOnlineTime) {
        this.lastOnlineTime = lastOnlineTime;
    }

    public long getNewMessageCount() {
        return newMessageCount;
    }

    public void setNewMessageCount(long newMessageCount) {
        this.newMessageCount = newMessageCount;
    }

    public long getNewMatchCount() {
        return newMatchCount;
    }

    public void setNewMatchCount(long newMatchCount) {
        this.newMatchCount = newMatchCount;
    }

    public long getNewLikeCount() {
        return newLikeCount;
    }

    public void setNewLikeCount(long newLikeCount) {
        this.newLikeCount = newLikeCount;
    }

    public long getNewProfiles() {
        return newProfiles;
    }

    public void setNewProfiles(long newProfiles) {
        this.newProfiles = newProfiles;
    }

    public String getPushType() {
        return pushType;
    }

    public void setPushType(String pushType) {
        this.pushType = pushType;
    }

    public boolean getNewLikeEnabled() {
        return newLikeEnabled;
    }

    public void setNewLikeEnabled(boolean newLikeEnabled) {
        this.newLikeEnabled = newLikeEnabled;
    }

    public boolean getNewMatchEnabled() {
        return newMatchEnabled;
    }

    public void setNewMatchEnabled(boolean newMatchEnabled) {
        this.newMatchEnabled = newMatchEnabled;
    }

    public boolean getNewMessageEnabled() {
        return newMessageEnabled;
    }

    public void setNewMessageEnabled(boolean newMessageEnabled) {
        this.newMessageEnabled = newMessageEnabled;
    }

    public String getOppositeUserId() {
        return oppositeUserId;
    }

    public void setOppositeUserId(String oppositeUserId) {
        this.oppositeUserId = oppositeUserId;
    }
}
