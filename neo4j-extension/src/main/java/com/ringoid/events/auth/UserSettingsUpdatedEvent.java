package com.ringoid.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSettingsUpdatedEvent extends BaseEvent {
    private String userId;
    private String locale;
    private Boolean wasLocaleChanged;
    private Boolean push;
    private Boolean wasPushChanged;
    private long timeZone;
    private Boolean wasTimeZoneChanged;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Boolean getPush() {
        return push;
    }

    public void setPush(Boolean push) {
        this.push = push;
    }

    public Boolean getWasPushChanged() {
        return wasPushChanged;
    }

    public void setWasPushChanged(Boolean wasPushChanged) {
        this.wasPushChanged = wasPushChanged;
    }

    public long getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(long timeZone) {
        this.timeZone = timeZone;
    }

    public Boolean getWasTimeZoneChanged() {
        return wasTimeZoneChanged;
    }

    public void setWasTimeZoneChanged(Boolean wasTimeZoneChanged) {
        this.wasTimeZoneChanged = wasTimeZoneChanged;
    }

    public Boolean getWasLocaleChanged() {
        return wasLocaleChanged;
    }

    public void setWasLocaleChanged(Boolean wasLocaleChanged) {
        this.wasLocaleChanged = wasLocaleChanged;
    }
}
