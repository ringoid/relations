package com.ringoid.events.preparenf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PrepareNFEvent extends BaseEvent {
    private String userId;
    private List<String> targetUserIds;

    public PrepareNFEvent() {
        this.targetUserIds = new ArrayList<>();
    }

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
}
