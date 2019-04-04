package com.ringoid;

public enum MatchProperties {
    MATCH_AT("match_at");

    private String propertyName;

    MatchProperties(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getPropertyName() {
        return propertyName;
    }
}
