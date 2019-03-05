package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private boolean wasYouSender;
    private String text;

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

    @Override
    public String toString() {
        return "Message{" +
                "wasYouSender=" + wasYouSender +
                ", text='" + text + '\'' +
                '}';
    }
}
