package com.ringoid;

public enum Relationships {
    UPLOAD_PHOTO,
    HIDE_PHOTO,
    RESIZED,

    VIEW,
    VIEW_IN_LIKES_YOU,
    VIEW_IN_MATCHES,
    VIEW_IN_MESSAGES,

    LIKE,
    BLOCK,
    MATCH,
    MESSAGE,

    TAKE_PART_IN_CONVERSATION,
    PASS_MESSAGE,

    VIEW_IN_HELLOS,
    VIEW_IN_INBOX,
    VIEW_IN_SENT,

    PREPARE_NF,

    UNSUPPORTED;

    public static Relationships fromString(String str) {
        switch (str.toUpperCase()) {
            case "UPLOAD_PHOTO":
                return UPLOAD_PHOTO;
            case "HIDE_PHOTO":
                return HIDE_PHOTO;
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
            case "TAKE_PART_IN_CONVERSATION":
                return TAKE_PART_IN_CONVERSATION;
            case "PASS_MESSAGE":
                return PASS_MESSAGE;
            case "RESIZED":
                return RESIZED;
            case "VIEW_IN_HELLOS":
                return VIEW_IN_HELLOS;
            case "VIEW_IN_INBOX":
                return VIEW_IN_INBOX;
            case "VIEW_IN_SENT":
                return VIEW_IN_SENT;
            case "PREPARE_NF":
                return PREPARE_NF;
            default:
                return UNSUPPORTED;
        }
    }
}
