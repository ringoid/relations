package com.ringoid.graphaware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ringoid.ConversationProperties;
import com.ringoid.Labels;
import com.ringoid.MessageProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.api.LMMRequest;
import com.ringoid.api.LMMResponse;
import com.ringoid.api.LikesYou;
import com.ringoid.api.Matches;
import com.ringoid.api.Messages;
import com.ringoid.api.NewFaces;
import com.ringoid.api.NewFacesRequest;
import com.ringoid.api.NewFacesResponse;
import com.ringoid.events.actions.ActionsUtils;
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
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.DeadlockDetectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

    private static final int BATCH_SIZE = 500;
    private static final int RETRIES = 5;
    private static final int BACKOFF = 100;

    private final GraphDatabaseService database;

    @Autowired
    public ActionController(GraphDatabaseService database) {
        this.database = database;
    }

    @RequestMapping(value = "/hello", method = RequestMethod.GET)
    @ResponseBody
    public String hello() {
        return "Hello World!";
    }

    @RequestMapping(value = "/create_indexes", method = RequestMethod.GET)
    @ResponseBody
    public String createIndexesApi() {
        createIndexes(database);
        return "OK";
    }

    @RequestMapping(value = "/likes_you", method = RequestMethod.GET)
    @ResponseBody
    public String likesYou(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMMRequest request = objectMapper.readValue(body, LMMRequest.class);
        LMMResponse response = LikesYou.likesYou(request, database);
        System.out.println("handle likes_you in " + (System.currentTimeMillis() - start) + " millis");
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/matches", method = RequestMethod.GET)
    @ResponseBody
    public String matches(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMMRequest request = objectMapper.readValue(body, LMMRequest.class);
        LMMResponse response = Matches.matches(request, database);
        System.out.println("handle matches in " + (System.currentTimeMillis() - start) + " millis");
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/messages", method = RequestMethod.GET)
    @ResponseBody
    public String messages(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMMRequest request = objectMapper.readValue(body, LMMRequest.class);
        LMMResponse response = Messages.messages(request, database);
        System.out.println("handle messages in " + (System.currentTimeMillis() - start) + " millis");
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/new_faces", method = RequestMethod.GET)
    @ResponseBody
    public String newFaces(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        NewFacesRequest request = objectMapper.readValue(body, NewFacesRequest.class);
        NewFacesResponse response = NewFaces.newFaces(request, database);
        System.out.println("handle new_faces in " + (System.currentTimeMillis() - start) + " millis");
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/actions", method = RequestMethod.POST)
    @ResponseBody
    public String actions(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(body);
        Iterator<JsonNode> it = node.get("events").elements();

        //todo:check that we move this code
        //createIndexes(database);

        int actionCounter = 0;
        try {
            List<JsonNode> batch = new ArrayList<>(BATCH_SIZE);
            while (it.hasNext()) {
                actionCounter++;
                JsonNode each = it.next();
                batch.add(each);
                if (batch.size() >= BATCH_SIZE) {
                    handleInTrasaction(batch, objectMapper);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                handleInTrasaction(batch, objectMapper);
                batch.clear();
            }

            System.out.println("(extension-report) complete handle " + actionCounter + " actions in " + (System.currentTimeMillis() - start) + " millis");
        } catch (IOException e) {
            throw new RuntimeException(e.getCause());
        }
        return "OK";
    }

    private void handleInTrasaction(List<JsonNode> list, ObjectMapper objectMapper) throws IOException {
        long start = System.currentTimeMillis();
        for (int i = 0; i < RETRIES; i++) {
            try (Transaction tx = database.beginTx()) {
                for (JsonNode each : list) {
                    doStuff(each, objectMapper);
                }
                tx.success();
                System.out.println("(extension-report) successfully handle " + list.size() + " actions in " + (System.currentTimeMillis() - start) + " millis");
                return;
            } catch (DeadlockDetectedException ex) {
                System.out.println("Catch a DeadlockDetectedException");
            }

            // Wait so that we don't immediately get into the same deadlock
            if (i < RETRIES - 1) {
                try {
                    Thread.sleep(BACKOFF);
                } catch (InterruptedException e) {
                    throw new TransactionFailureException("Interrupted", e);
                }
            }
        }
    }

    private void doStuff(JsonNode each, ObjectMapper objectMapper) throws IOException {
        String eventType = each.get("eventType").asText();
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
//                    ActionsUtils.likePhotoInternaly(event, database);
            ActionsUtils.likePhoto(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_VIEW_PHOTO.name())) {
            UserViewPhotoEvent event = objectMapper.readValue(each.traverse(), UserViewPhotoEvent.class);
//                    ActionsUtils.viewPhotoInternaly(event, database);
            ActionsUtils.viewPhoto(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_BLOCK_OTHER.name())) {
            UserBlockOtherEvent event = objectMapper.readValue(each.traverse(), UserBlockOtherEvent.class);
//                    ActionsUtils.blockInternaly(event, database);
            ActionsUtils.block(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_MESSAGE.name())) {
            UserMessageEvent event = objectMapper.readValue(each.traverse(), UserMessageEvent.class);
//                    ActionsUtils.messageInternal(event, database);
            ActionsUtils.message(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_UNLIKE_PHOTO.name())) {
            UserUnlikePhotoEvent event = objectMapper.readValue(each.traverse(), UserUnlikePhotoEvent.class);
//                    ActionsUtils.unlikeInternal(event, database);
            ActionsUtils.unlike(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_OPEN_CHAT.name())) {
            //todo:implement in next version
        }
    }

    private void createIndexes(GraphDatabaseService database) {
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.USER_ID.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.SEX.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.NEED_TO_MODERATE.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.LIKE_COUNTER.getPropertyName(), database);
        createIndex(Labels.PHOTO.getLabelName(), PhotoProperties.PHOTO_ID.getPropertyName(), database);
        createIndex(Labels.PHOTO.getLabelName(), PhotoProperties.NEED_TO_MODERATE.getPropertyName(), database);
        createIndex(Labels.CONVERSATION.getLabelName(), ConversationProperties.CONVERSATION_ID.getPropertyName(), database);
        createIndex(Labels.MESSAGE.getLabelName(), MessageProperties.MSG_ID.getPropertyName(), database);
    }

    private void createIndex(String label, String property, GraphDatabaseService database) {
        Transaction tx = database.beginTx();
        try {
            Schema schema = database.schema();
            for (IndexDefinition each : schema.getIndexes(Label.label(label))) {
                for (String eachProperty : each.getPropertyKeys()) {
                    if (Objects.equals(property, eachProperty)) {
                        return;
                    }
                }
            }

            schema.indexFor(Label.label(label))
                    .on(property)
                    .create();

            tx.success();

        } finally {
            tx.close();
        }
    }
}