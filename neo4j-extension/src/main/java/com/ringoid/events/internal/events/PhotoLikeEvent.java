package com.ringoid.events.internal.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ringoid.events.BaseEvent;

import static com.ringoid.events.EventTypes.INTERNAL_PHOTO_LIKE_EVENT;

@JsonIgnoreProperties
public class PhotoLikeEvent extends BaseEvent {
    private String userId;
    private String originPhotoId;

    public PhotoLikeEvent() {
    }

    public PhotoLikeEvent(String userId, String originPhotoId) {
        this.userId = userId;
        this.originPhotoId = originPhotoId;
        this.eventType = INTERNAL_PHOTO_LIKE_EVENT.name();
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOriginPhotoId() {
        return originPhotoId;
    }

    public void setOriginPhotoId(String originPhotoId) {
        this.originPhotoId = originPhotoId;
    }

    @Override
    public String toString() {
        return "PhotoLikeEvent{" +
                "userId='" + userId + '\'' +
                ", originPhotoId='" + originPhotoId + '\'' +
                '}';
    }
}
