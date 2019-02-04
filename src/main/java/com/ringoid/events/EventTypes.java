package com.ringoid.events;

public enum EventTypes {
    AUTH_USER_PROFILE_CREATED,
    AUTH_USER_SETTINGS_UPDATED,
    AUTH_USER_ONLINE,

    IMAGE_USER_UPLOAD_PHOTO,
    IMAGE_USER_DELETE_PHOTO,

    ACTION_USER_LIKE_PHOTO,
    ACTION_USER_VIEW_PHOTO,
    ACTION_USER_BLOCK_OTHER,
    ACTION_USER_UNLIKE_PHOTO,
    ACTION_USER_OPEN_CHAT,
    AUTH_USER_CALL_DELETE_HIMSELF,

    ACTION_USER_MESSAGE,

    INTERNAL_PHOTO_LIKE_EVENT, //this event goes outside to image service
    INTERNAL_USER_SEND_MESSAGE_EVENT, //this event goes to messages service
    INTERNAL_DELETE_USER_CONVERSATION_EVENT,//this event goes to message service
    INTERNAL_HIDE_PHOTO_EVENT,//this event goes to image service

    FEEDS_NEW_FACES_SEEN_PROFILES
}
