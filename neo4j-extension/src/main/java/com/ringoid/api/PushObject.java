package com.ringoid.api;

public class PushObject {

    private String userId;
    private String sex;
    private String locale;

    private long lastOnlineTime;

    private long newMessageCount;
    private long newMatchCount;
    private long newLikeCount;
    private long newProfiles;

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

    @Override
    public String toString() {
        return "PushObject{" +
                "userId='" + userId + '\'' +
                ", sex='" + sex + '\'' +
                ", locale='" + locale + '\'' +
                ", lastOnlineTime=" + lastOnlineTime +
                ", newMessageCount=" + newMessageCount +
                ", newMatchCount=" + newMatchCount +
                ", newLikeCount=" + newLikeCount +
                ", newProfiles=" + newProfiles +
                '}';
    }
}
