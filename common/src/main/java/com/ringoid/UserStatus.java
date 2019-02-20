package com.ringoid;

public enum UserStatus {
    ACTIVE("active"),
    HIDDEN("hidden");

    private String value;

    private UserStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
