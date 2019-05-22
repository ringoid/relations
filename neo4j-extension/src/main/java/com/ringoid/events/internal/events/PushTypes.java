package com.ringoid.events.internal.events;

public enum PushTypes {
    OnceDayPushType("ONCE_A_DAY_PUSH_TYPE"),
    NewLikeInternalEventType("INTERNAL_NEW_USER_LIKE_EVENT"),
    NewMatchInternalEventType("INTERNAL_NEW_USER_MATCH_EVENT"),
    NewMessageInternalEventType("INTERNAL_NEW_USER_MESSAGE_EVENT");

    private String name;

    PushTypes(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
