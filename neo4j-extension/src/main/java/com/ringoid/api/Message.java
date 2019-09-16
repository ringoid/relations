package com.ringoid.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private boolean wasYouSender;
    private String text;
    private String msgId;
    private long msgAt;
    private String clientMsgId;
    private boolean haveBeenRead;

    //use this property only for sorting inside engine
    private long wasSentAt;

    public boolean isHaveBeenRead() {
        return haveBeenRead;
    }

    public void setHaveBeenRead(boolean haveBeenRead) {
        this.haveBeenRead = haveBeenRead;
    }

    public String getClientMsgId() {
        return clientMsgId;
    }

    public void setClientMsgId(String clientMsgId) {
        this.clientMsgId = clientMsgId;
    }

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

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public long getMsgAt() {
        return msgAt;
    }

    public void setMsgAt(long msgAt) {
        this.msgAt = msgAt;
    }
}
