package com.ringoid;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.events.BaseEvent;
import com.ringoid.events.auth.AuthUtils;
import com.ringoid.events.auth.UserOnlineEvent;
import com.ringoid.events.auth.UserProfileCreatedEvent;
import com.ringoid.events.auth.UserSettingsUpdatedEvent;
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

import static com.ringoid.events.EventTypes.AUTH_USER_ONLINE;
import static com.ringoid.events.EventTypes.AUTH_USER_PROFILE_CREATED;
import static com.ringoid.events.EventTypes.AUTH_USER_SETTINGS_UPDATED;
import static com.ringoid.events.EventTypes.IMAGE_USER_DELETE_PHOTO;
import static com.ringoid.events.EventTypes.IMAGE_USER_UPLOAD_PHOTO;

public class KinesisConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;
    private final Gson gson;

    public KinesisConsumer() {
        String neo4jUri = System.getenv("NEO4J_URI");
        //todo:read these value from Security Storage
        String userName = "neo4j";
        String password = "i-0033e09261e4a06b5";

        driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(userName, password),
                Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());

        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();

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
            }
        }
        log.info("successfully handle event {}", event);
    }

}
