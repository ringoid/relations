package com.ringoid;

public enum Labels {
    PERSON("Person"),
    PHOTO("Photo"),
    RESIZED_PHOTO("ResizedPhoto"),
    CONVERSATION("Conversation"),
    MESSAGE("Message"),
    HIDDEN("Hidden");

    private final String labelName;

    Labels(String name) {
        this.labelName = name;
    }

    public String getLabelName() {
        return labelName;
    }
}
