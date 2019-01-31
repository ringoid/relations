package com.ringoid;

public enum Labels {
    PERSON("Person"),
    PHOTO("Photo"),
    HIDDEN("Hidden");

    private final String labelName;

    Labels(String name) {
        this.labelName = name;
    }

    public String getLabelName() {
        return labelName;
    }
}
