package com.ringoid.events;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.events.actions.ActionsUtils;
import com.ringoid.events.actions.UserBlockOtherEvent;
import com.ringoid.events.actions.UserLikePhotoEvent;
import com.ringoid.events.actions.UserUnlikePhotoEvent;
import com.ringoid.events.actions.UserViewPhotoEvent;
import com.ringoid.events.auth.AuthUtils;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
import com.ringoid.events.auth.UserOnlineEvent;
import com.ringoid.events.auth.UserProfileCreatedEvent;
import com.ringoid.events.auth.UserSettingsUpdatedEvent;
import com.ringoid.events.feeds.FeedsUtils;
import com.ringoid.events.feeds.ProfileWasReturnToNewFacesEvent;
import com.ringoid.events.image.ImageUtils;
import com.ringoid.events.image.UserDeletePhotoEvent;
import com.ringoid.events.image.UserUploadedPhotoEvent;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.ringoid.events.EventTypes.ACTION_USER_BLOCK_OTHER;
import static com.ringoid.events.EventTypes.ACTION_USER_LIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_UNLIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_VIEW_PHOTO;
import static com.ringoid.events.EventTypes.AUTH_USER_CALL_DELETE_HIMSELF;
import static com.ringoid.events.EventTypes.AUTH_USER_ONLINE;
import static com.ringoid.events.EventTypes.AUTH_USER_PROFILE_CREATED;
import static com.ringoid.events.EventTypes.AUTH_USER_SETTINGS_UPDATED;
import static com.ringoid.events.EventTypes.FEEDS_NEW_FACES_SEEN_PROFILES;
import static com.ringoid.events.EventTypes.IMAGE_USER_DELETE_PHOTO;
import static com.ringoid.events.EventTypes.IMAGE_USER_UPLOAD_PHOTO;

public class KinesisConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;
    private final Gson gson;
    private final AmazonKinesis kinesis;
    private final String internalStreamName;

    public KinesisConsumer() {
        String neo4jUri = System.getenv("NEO4J_URI");
        //todo:read these value from Security Storage
        String userName = System.getenv("NEO4J_USER");
        String password = System.getenv("NEO4J_PASSWORD");

        internalStreamName = System.getenv("INTERNAL_STREAM_NAME");

        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(userName, password),
                Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());

        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();

        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        kinesis = clientBuilder.build();
    }

    public void handler(KinesisEvent event, Context context) {
        log.debug("handle event {}", event);
        for (KinesisEvent.KinesisEventRecord each : event.getRecords()) {
            ByteBuffer buff = each.getKinesis().getData();
            String s = StandardCharsets.UTF_8.decode(buff).toString();
            log.debug("handle record string representation {}", s);
            BaseEvent baseEvent = gson.fromJson(s, BaseEvent.class);
            if (Objects.equals(baseEvent.getEventType(), AUTH_USER_PROFILE_CREATED.name())) {
                UserProfileCreatedEvent createProfileEvent = gson.fromJson(s, UserProfileCreatedEvent.class);
                AuthUtils.createProfile(createProfileEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), AUTH_USER_SETTINGS_UPDATED.name())) {
                UserSettingsUpdatedEvent updatedEvent = gson.fromJson(s, UserSettingsUpdatedEvent.class);
                AuthUtils.updateSettings(updatedEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), IMAGE_USER_UPLOAD_PHOTO.name())) {
                UserUploadedPhotoEvent uploadedPhotoEvent = gson.fromJson(s, UserUploadedPhotoEvent.class);
                ImageUtils.uploadPhoto(uploadedPhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), IMAGE_USER_DELETE_PHOTO.name())) {
                UserDeletePhotoEvent deletePhotoEvent = gson.fromJson(s, UserDeletePhotoEvent.class);
                ImageUtils.deletePhoto(deletePhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), AUTH_USER_ONLINE.name())) {
                UserOnlineEvent userOnlineEvent = gson.fromJson(s, UserOnlineEvent.class);
                AuthUtils.updateLastOnlineTime(userOnlineEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), AUTH_USER_CALL_DELETE_HIMSELF.name())) {
                UserCallDeleteHimselfEvent userCallDeleteHimselfEvent = gson.fromJson(s, UserCallDeleteHimselfEvent.class);
                AuthUtils.deleteUser(userCallDeleteHimselfEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_LIKE_PHOTO.name())) {
                UserLikePhotoEvent userLikePhotoEvent = gson.fromJson(s, UserLikePhotoEvent.class);
                ActionsUtils.likePhoto(userLikePhotoEvent, driver, kinesis, internalStreamName, gson);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_VIEW_PHOTO.name())) {
                UserViewPhotoEvent userViewPhotoEvent = gson.fromJson(s, UserViewPhotoEvent.class);
                ActionsUtils.viewPhoto(userViewPhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_BLOCK_OTHER.name())) {
                UserBlockOtherEvent userBlockOtherEvent = gson.fromJson(s, UserBlockOtherEvent.class);
                ActionsUtils.block(userBlockOtherEvent, driver, kinesis, internalStreamName, gson);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_UNLIKE_PHOTO.name())) {
                UserUnlikePhotoEvent userUnlikePhotoEvent = gson.fromJson(s, UserUnlikePhotoEvent.class);
                ActionsUtils.unlike(userUnlikePhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), FEEDS_NEW_FACES_SEEN_PROFILES.name())) {
                ProfileWasReturnToNewFacesEvent profileWasReturnToNewFacesEvent = gson.fromJson(s, ProfileWasReturnToNewFacesEvent.class);
                FeedsUtils.markAlreadySeenProfiles(profileWasReturnToNewFacesEvent, driver);
            }
        }
        log.info("successfully handle event {}", event);
    }

}
