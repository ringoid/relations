package com.ringoid.graphaware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringoid.events.actions.ActionsUtilsInternaly;
import com.ringoid.events.actions.UserBlockOtherEvent;
import com.ringoid.events.actions.UserLikePhotoEvent;
import com.ringoid.events.actions.UserMessageEvent;
import com.ringoid.events.actions.UserUnlikePhotoEvent;
import com.ringoid.events.actions.UserViewPhotoEvent;
import com.ringoid.events.auth.AuthUtilsInternaly;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
import com.ringoid.events.auth.UserOnlineEvent;
import com.ringoid.events.auth.UserProfileCreatedEvent;
import com.ringoid.events.auth.UserSettingsUpdatedEvent;
import com.ringoid.events.image.ImageUtilsInternaly;
import com.ringoid.events.image.UserDeletePhotoEvent;
import com.ringoid.events.image.UserUploadedPhotoEvent;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;

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

/**
 * Sample REST API for counting all nodes in the database.
 */
@Controller
public class ActionController {

    private final GraphDatabaseService database;
    private static final int TX_MAX = 500;

    @Autowired
    public ActionController(GraphDatabaseService database) {
        this.database = database;
    }

    @RequestMapping(value = "/actions", method = RequestMethod.POST)
    @ResponseBody
    public String actions(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(body);
        Iterator<JsonNode> it = node.get("events").elements();
        Transaction tx = database.beginTx();
        int actionCounter = 0;
        try {
            int txCounter = 0;
            while (it.hasNext()) {
                JsonNode each = it.next();
                String eventType = each.get("eventType").asText();
                actionCounter++;
                if (Objects.equals(eventType, AUTH_USER_ONLINE.name())) {
                    UserOnlineEvent event = objectMapper.readValue(each.traverse(), UserOnlineEvent.class);
                    AuthUtilsInternaly.updateLastOnlineTimeInternaly(event, database);
                } else if (Objects.equals(eventType, AUTH_USER_PROFILE_CREATED.name())) {
                    UserProfileCreatedEvent event = objectMapper.readValue(each.traverse(), UserProfileCreatedEvent.class);
                    AuthUtilsInternaly.createProfileInternaly(event, database);
                } else if (Objects.equals(eventType, AUTH_USER_SETTINGS_UPDATED.name())) {
                    UserSettingsUpdatedEvent event = objectMapper.readValue(each.traverse(), UserSettingsUpdatedEvent.class);
                    AuthUtilsInternaly.updateSettingsInternaly(event, database);
                } else if (Objects.equals(eventType, IMAGE_USER_UPLOAD_PHOTO.name())) {
                    UserUploadedPhotoEvent event = objectMapper.readValue(each.traverse(), UserUploadedPhotoEvent.class);
                    ImageUtilsInternaly.uploadPhotoInternaly(event, database);
                } else if (Objects.equals(eventType, IMAGE_USER_DELETE_PHOTO.name())) {
                    UserDeletePhotoEvent event = objectMapper.readValue(each.traverse(), UserDeletePhotoEvent.class);
                    ImageUtilsInternaly.deletePhotoInternaly(event, database);
                } else if (Objects.equals(eventType, AUTH_USER_CALL_DELETE_HIMSELF.name())) {
                    UserCallDeleteHimselfEvent event = objectMapper.readValue(each.traverse(), UserCallDeleteHimselfEvent.class);
                    AuthUtilsInternaly.deleteUserInternaly(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_LIKE_PHOTO.name())) {
                    UserLikePhotoEvent event = objectMapper.readValue(each.traverse(), UserLikePhotoEvent.class);
                    ActionsUtilsInternaly.likePhotoInternaly(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_VIEW_PHOTO.name())) {
                    UserViewPhotoEvent event = objectMapper.readValue(each.traverse(), UserViewPhotoEvent.class);
                    ActionsUtilsInternaly.viewPhotoInternaly(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_BLOCK_OTHER.name())) {
                    UserBlockOtherEvent event = objectMapper.readValue(each.traverse(), UserBlockOtherEvent.class);
                    ActionsUtilsInternaly.blockInternaly(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_MESSAGE.name())) {
                    UserMessageEvent event = objectMapper.readValue(each.traverse(), UserMessageEvent.class);
                    ActionsUtilsInternaly.messageInternal(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_UNLIKE_PHOTO.name())) {
                    UserUnlikePhotoEvent event = objectMapper.readValue(each.traverse(), UserUnlikePhotoEvent.class);
                    ActionsUtilsInternaly.unlikeInternal(event, database);
                } else if (Objects.equals(eventType, ACTION_USER_OPEN_CHAT.name())) {
                    //todo:implement in next version
                }

                txCounter++;
                if (txCounter >= TX_MAX) {
                    tx.success();
                    tx.close();
                    tx = database.beginTx();
                    txCounter = 0;
                }
            }

            tx.success();
            System.out.println("handle " + actionCounter + " actions in " + (System.currentTimeMillis() - start) + " millis");
        } catch (Exception e) {
            throw e;
        } finally {
            tx.close();
        }

        return "OK";
    }

}