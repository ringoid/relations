package com.ringoid;

public enum ViewRelationshipSource {
    NEW_FACES("new_faces"),
    WHO_LIKED_ME("who_liked_me"),
    MATCHES("matches"),
    MESSAGES("messages"),
    CHAT("chat");

    private String value;

    ViewRelationshipSource(String value) {
        this.value = value;
    }

    public static ViewRelationshipSource fromString(String str) {
        switch (str.toLowerCase()) {
            case "new_faces":
                return NEW_FACES;
            case "who_liked_me":
                return WHO_LIKED_ME;
            case "matches":
                return MATCHES;
            case "messages":
                return MESSAGES;
            case "chat":
                return CHAT;
            default:
                throw new IllegalArgumentException("Unsupported view source type " + str);
        }
    }

    public String getValue() {
        return value;
    }
}
