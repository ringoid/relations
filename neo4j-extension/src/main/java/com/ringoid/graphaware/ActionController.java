package com.ringoid.graphaware;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphaware.common.log.LoggerFactory;
import com.ringoid.ConversationProperties;
import com.ringoid.Labels;
import com.ringoid.MessageProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.api.ChatRequest;
import com.ringoid.api.ChatResponse;
import com.ringoid.api.Chats;
import com.ringoid.api.Discover;
import com.ringoid.api.DiscoverRequest;
import com.ringoid.api.GetLCLikes;
import com.ringoid.api.GetLCMessages;
import com.ringoid.api.LCRequest;
import com.ringoid.api.LCResponse;
import com.ringoid.api.LMHIS;
import com.ringoid.api.LMHISRequest;
import com.ringoid.api.LMHISResponse;
import com.ringoid.api.LikesYou;
import com.ringoid.api.Matches;
import com.ringoid.api.Messages;
import com.ringoid.api.NewFaces;
import com.ringoid.api.NewFacesRequest;
import com.ringoid.api.NewFacesResponse;
import com.ringoid.api.PrepareNewFacesRequest;
import com.ringoid.api.PrepareNewFacesResponse;
import com.ringoid.api.PushRequest;
import com.ringoid.api.PushResponse;
import com.ringoid.api.WhoReadyForPush;
import com.ringoid.api.internaltmp.ConvertRequest;
import com.ringoid.api.internaltmp.ConvertResponse;
import com.ringoid.api.internaltmp.ConvertToThumbnailsFunction;
import com.ringoid.events.actions.ActionsUtils;
import com.ringoid.events.actions.UserBlockOtherEvent;
import com.ringoid.events.actions.UserChangedLocationEvent;
import com.ringoid.events.actions.UserLikePhotoEvent;
import com.ringoid.events.actions.UserMessageEvent;
import com.ringoid.events.actions.UserReadMessageEvent;
import com.ringoid.events.actions.UserUnlikePhotoEvent;
import com.ringoid.events.actions.UserViewChatEvent;
import com.ringoid.events.actions.UserViewPhotoEvent;
import com.ringoid.events.auth.AuthUtilsInternaly;
import com.ringoid.events.auth.UserCallDeleteHimselfEvent;
import com.ringoid.events.auth.UserClaimReferralCodeEvent;
import com.ringoid.events.auth.UserOnlineEvent;
import com.ringoid.events.auth.UserProfileCreatedEvent;
import com.ringoid.events.auth.UserSettingsUpdatedEvent;
import com.ringoid.events.auth.UserUpdateProfileEvent;
import com.ringoid.events.image.ImageUtilsInternaly;
import com.ringoid.events.image.ResizePhotoEvent;
import com.ringoid.events.image.UserDeletePhotoEvent;
import com.ringoid.events.image.UserUploadedPhotoEvent;
import com.ringoid.events.preparenf.DeletePreviousPreparedNFEvent;
import com.ringoid.events.preparenf.PrepareNFEvent;
import com.ringoid.events.preparenf.PrepareNFUtils;
import com.ringoid.events.push.PushUtils;
import com.ringoid.events.push.PushWasSentEvent;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.TransactionFailureException;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.kernel.DeadlockDetectedException;
import org.neo4j.logging.Log;
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

import static com.ringoid.events.EventTypes.ACTION_READ_MESSAGE;
import static com.ringoid.events.EventTypes.ACTION_USER_BLOCK_OTHER;
import static com.ringoid.events.EventTypes.ACTION_USER_CHANGE_LOCATION;
import static com.ringoid.events.EventTypes.ACTION_USER_LIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_MESSAGE;
import static com.ringoid.events.EventTypes.ACTION_USER_UNLIKE_PHOTO;
import static com.ringoid.events.EventTypes.ACTION_USER_VIEW_CHAT;
import static com.ringoid.events.EventTypes.ACTION_USER_VIEW_PHOTO;
import static com.ringoid.events.EventTypes.AUTH_USER_CALL_DELETE_HIMSELF;
import static com.ringoid.events.EventTypes.AUTH_USER_CLAIM_REFERRAL_CODE;
import static com.ringoid.events.EventTypes.AUTH_USER_ONLINE;
import static com.ringoid.events.EventTypes.AUTH_USER_PROFILE_CREATED;
import static com.ringoid.events.EventTypes.AUTH_USER_SETTINGS_UPDATED;
import static com.ringoid.events.EventTypes.AUTH_USER_UPDATE_PROFILE;
import static com.ringoid.events.EventTypes.DELETE_PREPARED_NEW_FACES_EVENT;
import static com.ringoid.events.EventTypes.IMAGE_USER_DELETE_PHOTO;
import static com.ringoid.events.EventTypes.IMAGE_USER_UPLOAD_PHOTO;
import static com.ringoid.events.EventTypes.INTERNAL_RESIZE_PHOTO_EVENT;
import static com.ringoid.events.EventTypes.PREPARE_NEW_FACES_EVENT;
import static com.ringoid.events.EventTypes.PUSH_WAS_SENT;

/**
 * Sample REST API for counting all nodes in the database.
 */
@Controller
public class ActionController {

    private final Log log = LoggerFactory.getLogger(getClass());

    private static final int BATCH_SIZE = 500;
    private static final int RETRIES = 5;
    private static final int BACKOFF = 100;

    private static final int NEW_FACES_HARDCODE_LIMIT_FOR_FAST_SEARCH = 20;
//    private static final int NEW_FACES_HARDCODE_LIMIT = 40;
//    private static final int PREPARE_NEW_FACES_HARDCORE_LIMIT = 100;

    private final GraphDatabaseService database;
    private final MetricRegistry metrics;

    @Autowired
    public ActionController(GraphDatabaseService database, MetricRegistry metrics) {
        this.database = database;
        this.metrics = metrics;
    }

    @RequestMapping(value = "/hello", method = RequestMethod.GET)
    @ResponseBody
    public String hello() {
        log.info("hello world endpoint");
        return "Hello World!";
    }

    @RequestMapping(value = "/get_metrics", method = RequestMethod.GET)
    @ResponseBody
    public String getMetrics() {
//        String result = metricsToString("likes_you_full");
//        result += ",\n";
//        result += metricsToString("matches_full");
//        result += ",\n";
//        result += metricsToString("messages_full");
//        result += ",\n";
//        result += metricsToString("lmhis_full");
//        result += ",\n";
//        result += metricsToString("chat_full");
//        result += ",\n";
//        result += metricsToString("new_faces_full");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopByUnseenPart");
//        result += ",\n";
//        result += metricsToString("new_faces_createProfileListWithResizedAndSortedPhotos");
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart");
//        result += ",\n";
//        result += metricsToString("new_faces_commonSortProfilesSeenPart");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopByUnseenPart_without_geo_full");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopByUnseenPart_geo_full");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart_without_geo_full_target_male");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart_without_geo_full_target_female");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart_geo_full_target_male");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart_geo_full_target_female");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopBySeenPart_geo_steps");
//
//        result += ",\n";
//        result += metricsToString("new_faces_loopByUnseenPart_geo_steps");
//
//        result += ",\n";
//        result += metricsToString("prepare_new_faces_loopByUnseenPart");
//
//        result += ",\n";
//        result += metricsToString("prepare_new_faces_loopBySeenPart");
//
//        result += ",\n";
//        result += metricsToString("prepare_new_faces_full");
//
//        result += ",\n";
//        result += metricsToString("new_faces_no_prepared_full");
//
//        result += ",\n";
//        result += metricsToString("new_faces_prepared_full");
//
//        result += ",\n";
        String result = metricsToString("discover_full");

        result += ",\n";
        result += metricsToString("get_lc_likes_full");

        result += ",\n";
        result += metricsToString("get_lc_messages_full");

        result += ",\n";
        result += metricsToString("discover_unseen_part");

        result += ",\n";
        result += metricsToString("discover_seen_part");

        result += ",\n";
        result += metricsToString("discover_online_unseen_filtered_result");

        result += ",\n";
        result += metricsToString("discover_active_unseen_filtered_result");

        return result;
    }

    private String metricsToString(final String name) {
        return String.format(
                "{\"name\":\"%s\"," +
                        "\"count\":%s," +
                        "\"min\":%s," +
                        "\"max\":%s," +
                        "\"mean\":%s," +
                        "\"median\":%s," +
                        "\"75%%\":%s," +
                        "\"95%%\":%s," +
                        "\"98%%\":%s," +
                        "\"99%%\":%s}",
                name,
                Long.toString(metrics.histogram(name).getCount()),
                Long.toString(metrics.histogram(name).getSnapshot().getMin()),
                Long.toString(metrics.histogram(name).getSnapshot().getMax()),
                Double.toString(metrics.histogram(name).getSnapshot().getMean()),
                Double.toString(metrics.histogram(name).getSnapshot().getMedian()),
                Double.toString(metrics.histogram(name).getSnapshot().get75thPercentile()),
                Double.toString(metrics.histogram(name).getSnapshot().get95thPercentile()),
                Double.toString(metrics.histogram(name).getSnapshot().get98thPercentile()),
                Double.toString(metrics.histogram(name).getSnapshot().get99thPercentile())
        );
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
        LMHISRequest request = objectMapper.readValue(body, LMHISRequest.class);
        LMHISResponse response = LikesYou.likesYou(request, database);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle likes_you for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getProfiles().size(), fullTime);
        metrics.histogram("likes_you_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/matches", method = RequestMethod.GET)
    @ResponseBody
    public String matches(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMHISRequest request = objectMapper.readValue(body, LMHISRequest.class);
        LMHISResponse response = Matches.matches(request, database);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle matches for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getProfiles().size(), fullTime);
        metrics.histogram("matches_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/messages", method = RequestMethod.GET)
    @ResponseBody
    public String messages(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMHISRequest request = objectMapper.readValue(body, LMHISRequest.class);
        LMHISResponse response = Messages.messages(request, database);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle messages for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getProfiles().size(), fullTime);
        metrics.histogram("messages_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/chat", method = RequestMethod.GET)
    @ResponseBody
    public String chat(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        ChatRequest request = objectMapper.readValue(body, ChatRequest.class);
        ChatResponse response = Chats.chat(request, database);
        long fullTime = System.currentTimeMillis() - start;
        int msgCount = 0;
        if (Objects.nonNull(response.getProfile()) && Objects.nonNull(response.getProfile().getMessages())) {
            msgCount = response.getProfile().getMessages().size();
        }
        log.info("handle chat for userId [%s] and oppositeUserId [%s] with result size %s in %s millis",
                request.getUserId(), request.getOppositeUserId(), msgCount, fullTime);
        metrics.histogram("chat_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/lmhis", method = RequestMethod.GET)
    @ResponseBody
    public String lmhis(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LMHISRequest request = objectMapper.readValue(body, LMHISRequest.class);
        LMHISResponse response = LMHIS.lmHis(request, database);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle lmhis for userId [%s] for %s part with result size %s in %s millis",
                request.getUserId(), request.getLmhisPart(), response.getProfiles().size(), fullTime);
        metrics.histogram("lmhis_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/discover", method = RequestMethod.GET)
    @ResponseBody
    public String discover(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        DiscoverRequest request = objectMapper.readValue(body, DiscoverRequest.class);
        NewFacesResponse response = Discover.discover(request, database, metrics);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle discover for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getNewFaces().size(), fullTime);
        metrics.histogram("discover_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/get_lc_likes", method = RequestMethod.GET)
    @ResponseBody
    public String getLcLikes(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LCRequest request = objectMapper.readValue(body, LCRequest.class);
        LCResponse response = GetLCLikes.likesYou(request, database, metrics);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle get_lc likes for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getProfiles().size(), fullTime);
        metrics.histogram("get_lc_likes_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/how_user_see_get_lc_likes", method = RequestMethod.GET)
    @ResponseBody
    public String howUserSeeGetLcLikes(@RequestBody String body) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        LCRequest request = objectMapper.readValue(body, LCRequest.class);
        LCResponse response = GetLCLikes.howUserSeeLikes(request, database, metrics);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/get_lc_messages", method = RequestMethod.GET)
    @ResponseBody
    public String getLcMessages(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        LCRequest request = objectMapper.readValue(body, LCRequest.class);
        LCResponse response = GetLCMessages.messages(request, database, metrics);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle get_lc messages for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getProfiles().size(), fullTime);
        metrics.histogram("get_lc_messages_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/new_faces", method = RequestMethod.GET)
    @ResponseBody
    public String newFaces(@RequestBody String body) throws IOException {
//        long start = System.currentTimeMillis();
//        ObjectMapper objectMapper = new ObjectMapper();
//        NewFacesRequest request = objectMapper.readValue(body, NewFacesRequest.class);
//        //IGNORE CLIENT SIDE LIMIT AND HARDCODE OWN
//        request.setLimit(100);
//        NewFacesResponse response = NewFaces.newFaces(request, database, metrics);
//        long fullTime = System.currentTimeMillis() - start;
//        log.info("handle new_faces with for userId [%s] with result size %s in %s millis",
//                request.getUserId(), response.getNewFaces().size(), fullTime);
//        metrics.histogram("new_faces_full").update(fullTime);
//        return objectMapper.writeValueAsString(response);

        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        NewFacesRequest request = objectMapper.readValue(body, NewFacesRequest.class);
        NewFacesResponse response = NewFaces.newFacesWithPreparedNodes(request, database, metrics);
        if (response.getNewFaces().isEmpty()) {
            if (request.getLimit() > NEW_FACES_HARDCODE_LIMIT_FOR_FAST_SEARCH) {
                request.setLimit(NEW_FACES_HARDCODE_LIMIT_FOR_FAST_SEARCH);
            }
            response = NewFaces.newFaces(request, database, metrics);
            long fullTime = System.currentTimeMillis() - start;
            metrics.histogram("new_faces_prepared_full").update(fullTime);
            log.info("handle new_faces WITHOUT prepared profiles for userId [%s] with result size %s in %s millis",
                    request.getUserId(), response.getNewFaces().size(), fullTime);
        } else {
            long fullTime = System.currentTimeMillis() - start;
            metrics.histogram("new_faces_no_prepared_full").update(fullTime);
            log.info("handle new_faces with prepared profiles for userId [%s] with result size %s in %s millis",
                    request.getUserId(), response.getNewFaces().size(), fullTime);
        }
        long fullTime = System.currentTimeMillis() - start;
        metrics.histogram("new_faces_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/prepare_new_faces", method = RequestMethod.GET)
    @ResponseBody
    public String prepareNewFaces(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        PrepareNewFacesRequest request = objectMapper.readValue(body, PrepareNewFacesRequest.class);
        //IGNORE CLIENT SIDE LIMIT AND HARDCODE OWN
//        request.setLimit(PREPARE_NEW_FACES_HARDCORE_LIMIT);
        PrepareNewFacesResponse response = NewFaces.prepareNewFaces(request, database, metrics);
        long fullTime = System.currentTimeMillis() - start;
        log.info("handle prepare_new_faces with for userId [%s] with result size %s in %s millis",
                request.getUserId(), response.getTargetUserIndexMap().size(), fullTime);
        metrics.histogram("prepare_new_faces_full").update(fullTime);
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/ready_for_push", method = RequestMethod.GET)
    @ResponseBody
    public String readyForPush(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        PushRequest request = objectMapper.readValue(body, PushRequest.class);
        PushResponse response = WhoReadyForPush.whoAreReady(request, database);
        log.info("handle ready_for_push with result size %s in %s millis", response.getUsers().size(), (System.currentTimeMillis() - start));
        return objectMapper.writeValueAsString(response);
    }

    @RequestMapping(value = "/fetch_for_convertion", method = RequestMethod.GET)
    @ResponseBody
    public String fetchForConversion(@RequestBody String body) throws IOException {
        long start = System.currentTimeMillis();
        ObjectMapper objectMapper = new ObjectMapper();
        ConvertRequest request = objectMapper.readValue(body, ConvertRequest.class);
        ConvertResponse response = ConvertToThumbnailsFunction.fetchForConvertion(request, database);
        log.info("handle fetch_for_convertion with result size %s in %s millis", response.getObjects().size(), (System.currentTimeMillis() - start));
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

            log.info("(extension-report-complete-handle) complete handle %s actions in %s millis", actionCounter, (System.currentTimeMillis() - start));
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
                log.info("(extension-report-successfully-commit-tx) successfully handle %s actions in %s millis", list.size(), (System.currentTimeMillis() - start));
                return;
            } catch (DeadlockDetectedException ex) {
                log.warn("catch a DeadlockDetectedException on %s retries, lets sleep for %s millis", i, BACKOFF);
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
        } else if (Objects.equals(eventType, IMAGE_USER_UPLOAD_PHOTO.name())) {
            UserUploadedPhotoEvent event = objectMapper.readValue(each.traverse(), UserUploadedPhotoEvent.class);
            ImageUtilsInternaly.uploadPhotoInternaly(event, database);
        } else if (Objects.equals(eventType, IMAGE_USER_DELETE_PHOTO.name())) {
            UserDeletePhotoEvent event = objectMapper.readValue(each.traverse(), UserDeletePhotoEvent.class);
            ImageUtilsInternaly.deletePhotoInternaly(event, database);
        } else if (Objects.equals(eventType, INTERNAL_RESIZE_PHOTO_EVENT.name())) {
            ResizePhotoEvent event = objectMapper.readValue(each.traverse(), ResizePhotoEvent.class);
            ImageUtilsInternaly.resizedPhotoInternaly(event, database);
        } else if (Objects.equals(eventType, AUTH_USER_CALL_DELETE_HIMSELF.name())) {
            UserCallDeleteHimselfEvent event = objectMapper.readValue(each.traverse(), UserCallDeleteHimselfEvent.class);
            AuthUtilsInternaly.deleteUserInternaly(event, database);
        } else if (Objects.equals(eventType, AUTH_USER_CLAIM_REFERRAL_CODE.name())) {
            UserClaimReferralCodeEvent event = objectMapper.readValue(each.traverse(), UserClaimReferralCodeEvent.class);
            AuthUtilsInternaly.claimReferralCodeInternaly(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_LIKE_PHOTO.name())) {
            UserLikePhotoEvent event = objectMapper.readValue(each.traverse(), UserLikePhotoEvent.class);
            ActionsUtils.likePhoto(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_VIEW_PHOTO.name())) {
            UserViewPhotoEvent event = objectMapper.readValue(each.traverse(), UserViewPhotoEvent.class);
            ActionsUtils.viewPhoto(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_BLOCK_OTHER.name())) {
            UserBlockOtherEvent event = objectMapper.readValue(each.traverse(), UserBlockOtherEvent.class);
            ActionsUtils.block(event, database);
        } else if (Objects.equals(eventType, AUTH_USER_SETTINGS_UPDATED.name())) {
            UserSettingsUpdatedEvent event = objectMapper.readValue(each.traverse(), UserSettingsUpdatedEvent.class);
            AuthUtilsInternaly.updateSettings(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_MESSAGE.name())) {
            UserMessageEvent event = objectMapper.readValue(each.traverse(), UserMessageEvent.class);
            ActionsUtils.message(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_UNLIKE_PHOTO.name())) {
            UserUnlikePhotoEvent event = objectMapper.readValue(each.traverse(), UserUnlikePhotoEvent.class);
            ActionsUtils.unlike(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_VIEW_CHAT.name())) {
            UserViewChatEvent event = objectMapper.readValue(each.traverse(), UserViewChatEvent.class);
            ActionsUtils.viewChat(event, database);
        } else if (Objects.equals(eventType, PUSH_WAS_SENT.name())) {
            PushWasSentEvent event = objectMapper.readValue(each.traverse(), PushWasSentEvent.class);
            PushUtils.pushWasSent(event, database);
        } else if (Objects.equals(eventType, ACTION_USER_CHANGE_LOCATION.name())) {
            UserChangedLocationEvent event = objectMapper.readValue(each.traverse(), UserChangedLocationEvent.class);
            ActionsUtils.updateLocation(event, database);
        } else if (Objects.equals(eventType, AUTH_USER_UPDATE_PROFILE.name())) {
            UserUpdateProfileEvent event = objectMapper.readValue(each.traverse(), UserUpdateProfileEvent.class);
            AuthUtilsInternaly.updateProfile(event, database);
        } else if (Objects.equals(eventType, PREPARE_NEW_FACES_EVENT.name())) {
            PrepareNFEvent event = objectMapper.readValue(each.traverse(), PrepareNFEvent.class);
            PrepareNFUtils.prepareNF(event, database);
        } else if (Objects.equals(eventType, DELETE_PREPARED_NEW_FACES_EVENT.name())) {
            DeletePreviousPreparedNFEvent event = objectMapper.readValue(each.traverse(), DeletePreviousPreparedNFEvent.class);
            PrepareNFUtils.deletePreparedNF(event, database);
        } else if (Objects.equals(eventType, ACTION_READ_MESSAGE.name())) {
            UserReadMessageEvent event = objectMapper.readValue(each.traverse(), UserReadMessageEvent.class);
            ActionsUtils.readMessage(event, database);
        }
    }

    private void createIndexes(GraphDatabaseService database) {
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.USER_ID.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.SEX.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.NEED_TO_MODERATE.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.LIKE_COUNTER.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.PUSH_WAS_SENT_AT.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.LAST_ONLINE_TIME.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.CREATED.getPropertyName(), database);
        createIndex(Labels.PERSON.getLabelName(), PersonProperties.LOCATION.getPropertyName(), database);

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
                        log.debug("index for :%s(%s) already exist", label, property);
                        return;
                    }
                }
            }

            schema.indexFor(Label.label(label))
                    .on(property)
                    .create();

            tx.success();
            log.info("index for :%s(%s) was successfully created", label, property);
        } finally {
            tx.close();
        }
    }
}