package com.ringoid;

public enum Relationships {
    UPLOAD_PHOTO,
    LIKE,
    VIEW,
    BLOCK,
    MATCH,
    MESSAGE,
    UNSUPPORTED;

    public static Relationships fromString(String str) {
        switch (str.toUpperCase()) {
            case "UPLOAD_PHOTO":
                return UPLOAD_PHOTO;
            case "LIKE":
                return LIKE;
            case "VIEW":
                return VIEW;
            case "BLOCK":
                return BLOCK;
            case "MATCH":
                return MATCH;
            case "MESSAGE":
                return MESSAGE;
            default:
                return UNSUPPORTED;
        }
    }
}
