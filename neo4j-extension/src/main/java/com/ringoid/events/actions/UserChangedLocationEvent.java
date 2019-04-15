package com.ringoid.events.actions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserChangedLocationEvent extends BaseEvent {
    private String userId;
    private float lat;
    private float lon;
    private long updatedLocationTimeAt;
    private long unixTime;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public float getLat() {
        return lat;
    }

    public void setLat(float lat) {
        this.lat = lat;
    }

    public float getLon() {
        return lon;
    }

    public void setLon(float lon) {
        this.lon = lon;
    }

    public long getUpdatedLocationTimeAt() {
        return updatedLocationTimeAt;
    }

    public void setUpdatedLocationTimeAt(long updatedLocationTimeAt) {
        this.updatedLocationTimeAt = updatedLocationTimeAt;
    }

    public long getUnixTime() {
        return unixTime;
    }

    public void setUnixTime(long unixTime) {
        this.unixTime = unixTime;
    }
}
