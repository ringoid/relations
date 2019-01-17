package com.ringoid;

public enum Relationships {
    UPLOAD_PHOTO,
    DELETE_PHOTO,
    LIKE,
    VIEW,
    VIEW_IN_LIKES_YOU,
    VIEW_IN_MATCHES,
    VIEW_IN_MESSAGES,
    BLOCK,
    MATCH,
    MESSAGE,
    UNSUPPORTED;

    public static Relationships fromString(String str) {
        switch (str.toUpperCase()) {
            case "UPLOAD_PHOTO":
                return UPLOAD_PHOTO;
            case "DELETE_PHOTO":
                return DELETE_PHOTO;
            case "LIKE":
                return LIKE;
            case "VIEW":
                return VIEW;
            case "VIEW_IN_LIKES_YOU":
                return VIEW_IN_LIKES_YOU;
            case "VIEW_IN_MATCHES":
                return VIEW_IN_MATCHES;
            case "VIEW_IN_MESSAGES":
                return VIEW_IN_MESSAGES;
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
