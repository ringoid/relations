package com.ringoid;

public enum Relationships {
    UPLOAD_PHOTO,
    LIKE,
    VIEW,
    VIEW_IN_LIKES_YOU,
    VIEW_IN_MATCHES,
    VIEW_IN_MESSAGES,
    BLOCK,
    MATCH,
    MESSAGE,
    WAS_RETURN_TO_NEW_FACES,
    UNSUPPORTED;

    public static Relationships fromString(String str) {
        switch (str.toUpperCase()) {
            case "UPLOAD_PHOTO":
                return UPLOAD_PHOTO;
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
            case "WAS_RETURN_TO_NEW_FACES":
                return WAS_RETURN_TO_NEW_FACES;
            default:
                return UNSUPPORTED;
        }
    }
}
