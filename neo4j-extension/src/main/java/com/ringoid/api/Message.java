package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private boolean wasYouSender;
    private String text;

    //use this property only for sorting inside engine
    private long wasSentAt;

    public boolean isWasYouSender() {
        return wasYouSender;
    }

    public void setWasYouSender(boolean wasYouSender) {
        this.wasYouSender = wasYouSender;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getWasSentAt() {
        return wasSentAt;
    }

    public void setWasSentAt(long wasSentAt) {
        this.wasSentAt = wasSentAt;
    }

}
