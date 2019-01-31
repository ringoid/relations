package com.ringoid.events;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.AWSSecretsManagerClientBuilder;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ringoid.Labels;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.events.actions.ActionsUtils;
import com.ringoid.events.actions.UserBlockOtherEvent;
import com.ringoid.events.actions.UserLikePhotoEvent;
import com.ringoid.events.actions.UserMessageEvent;
import com.ringoid.events.actions.UserUnlikePhotoEvent;
import com.ringoid.events.actions.UserViewPhotoEvent;
import com.ringoid.events.auth.AuthUtils;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
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
import org.neo4j.driver.v1.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.ringoid.events.EventTypes.ACTION_USER_BLOCK_OTHER;
import static com.ringoid.events.EventTypes.ACTION_USER_LIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_MESSAGE;
import static com.ringoid.events.EventTypes.ACTION_USER_OPEN_CHAT;
import static com.ringoid.events.EventTypes.ACTION_USER_UNLIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_VIEW_PHOTO;
import static com.ringoid.events.EventTypes.AUTH_USER_CALL_DELETE_HIMSELF;
import static com.ringoid.events.EventTypes.AUTH_USER_ONLINE;
import static com.ringoid.events.EventTypes.AUTH_USER_PROFILE_CREATED;
import static com.ringoid.events.EventTypes.AUTH_USER_SETTINGS_UPDATED;
import static com.ringoid.events.EventTypes.IMAGE_USER_DELETE_PHOTO;
import static com.ringoid.events.EventTypes.IMAGE_USER_UPLOAD_PHOTO;

public class KinesisConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Driver driver;
    private final Gson gson;
    private final AmazonKinesis kinesis;
    private final String internalStreamName;
    private final AmazonSQS sqs;
    private final String botSqsQueueUrl;
    private final boolean botEnabled;

    public KinesisConsumer() {
        GsonBuilder builder = new GsonBuilder();
        gson = builder.create();

        String env = System.getenv("ENV");
        String userName = System.getenv("NEO4J_USER");
        String neo4jUris = System.getenv("NEO4J_URIS");

        // Create a Secrets Manager client
        AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
                .withRegion("eu-west-1")
                .build();

        GetSecretValueRequest getSecretValueRequest = new GetSecretValueRequest()
                .withSecretId(env + "/Neo4j/Password");
        GetSecretValueResult getSecretValueResult = null;

        try {
            getSecretValueResult = client.getSecretValue(getSecretValueRequest);
        } catch (Exception e) {
            log.error("error fetching secret", e);
            throw e;
        }

        String secret = getSecretValueResult.getSecretString();
        HashMap<String, String> map = gson.fromJson(secret, (new HashMap<String, String>()).getClass());
        String password = map.get("password");

        String[] arr = neo4jUris.split("&");
        log.info("there is a list of ips {}", arr);
        if (arr.length > 1) {
            List<URI> uris = new ArrayList<>();
            for (String each : arr) {
                uris.add(URI.create("bolt+routing://" + each + ":7687"));
            }
            driver = GraphDatabase.routingDriver(uris, AuthTokens.basic(userName, password),
                    Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
        } else {
            driver = GraphDatabase.driver("bolt://" + arr[0] + ":7687", AuthTokens.basic(userName, password),
                    Config.build().withMaxTransactionRetryTime(10, TimeUnit.SECONDS).toConfig());
        }

        internalStreamName = System.getenv("INTERNAL_STREAM_NAME");

        botSqsQueueUrl = System.getenv("BOT_SQS_QUEUE_URL");

        botEnabled = Boolean.valueOf(System.getenv("BOTS_ENABLED"));


        AmazonKinesisClientBuilder clientBuilder = AmazonKinesisClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        kinesis = clientBuilder.build();

        AmazonSQSClientBuilder sqsClientBuilder = AmazonSQSClientBuilder.standard().withRegion(Regions.EU_WEST_1);
        sqs = sqsClientBuilder.build();
    }

    private void createIndexes(Driver driver) {
        String createIndexQuery = "CREATE INDEX ON :%s(%s)";
        String userIdPersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.USER_ID.getPropertyName()
        );
        String sexPersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.SEX.getPropertyName()
        );
        String moderatePersonIndexQuery = String.format(
                createIndexQuery, Labels.PERSON.getLabelName(), PersonProperties.NEED_TO_MODERATE.getPropertyName()
        );
        String photoIdPhotoIndexQuery = String.format(
                createIndexQuery, Labels.PHOTO.getLabelName(), PhotoProperties.PHOTO_ID.getPropertyName()
        );
        String moderatePhotoIndexQuery = String.format(
                createIndexQuery, Labels.PHOTO.getLabelName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName()
        );
        try (Session session = driver.session()) {
            session.run(userIdPersonIndexQuery);
            session.run(sexPersonIndexQuery);
            session.run(moderatePersonIndexQuery);
            session.run(photoIdPhotoIndexQuery);
            session.run(moderatePhotoIndexQuery);
        }
    }

    public void handler(KinesisEvent event, Context context) {
        createIndexes(driver);
        log.debug("handle event {}", event);
        for (KinesisEvent.KinesisEventRecord each : event.getRecords()) {
//            try {
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
                AuthUtils.deleteUser(userCallDeleteHimselfEvent, driver, kinesis, internalStreamName, gson);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_LIKE_PHOTO.name())) {
                UserLikePhotoEvent userLikePhotoEvent = gson.fromJson(s, UserLikePhotoEvent.class);
                ActionsUtils.likePhoto(userLikePhotoEvent, driver, kinesis, internalStreamName, gson, sqs, botSqsQueueUrl, botEnabled);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_VIEW_PHOTO.name())) {
                UserViewPhotoEvent userViewPhotoEvent = gson.fromJson(s, UserViewPhotoEvent.class);
                ActionsUtils.viewPhoto(userViewPhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_BLOCK_OTHER.name())) {
                UserBlockOtherEvent userBlockOtherEvent = gson.fromJson(s, UserBlockOtherEvent.class);
                ActionsUtils.block(userBlockOtherEvent, driver, kinesis, internalStreamName, gson);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_MESSAGE.name())) {
                UserMessageEvent userMessage = gson.fromJson(s, UserMessageEvent.class);
                ActionsUtils.message(userMessage, driver, kinesis, internalStreamName, gson, sqs, botSqsQueueUrl, botEnabled);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_UNLIKE_PHOTO.name())) {
                UserUnlikePhotoEvent userUnlikePhotoEvent = gson.fromJson(s, UserUnlikePhotoEvent.class);
                ActionsUtils.unlike(userUnlikePhotoEvent, driver);
            } else if (Objects.equals(baseEvent.getEventType(), ACTION_USER_OPEN_CHAT.name())) {
                //todo:implement later if we will need it
            }
//            } catch (Neo4jException neo4jEx) {
//                throw neo4jEx;
//            } catch (Exception e) {
//                //todo:add alarm or something like that
//                log.error("error handle {} event from the stream, skip it", each);
//            }
        }
        log.info("successfully handle event {}", event);
    }

}
