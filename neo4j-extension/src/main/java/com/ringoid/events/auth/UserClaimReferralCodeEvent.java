package com.ringoid.events.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserClaimReferralCodeEvent extends BaseEvent {
    private String userId;
    private String referralId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReferralId() {
        return referralId;
    }

    public void setReferralId(String referralId) {
        this.referralId = referralId;
    }
}
