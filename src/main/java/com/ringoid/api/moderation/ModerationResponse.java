package com.ringoid.api.moderation;

import java.util.List;

public class ModerationResponse {
    private List<ProfileObj> profiles;

    public ModerationResponse() {
    }

    public List<ProfileObj> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<ProfileObj> profiles) {
        this.profiles = profiles;
    }
}
