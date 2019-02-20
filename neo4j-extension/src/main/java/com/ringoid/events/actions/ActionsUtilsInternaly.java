package com.ringoid.events.actions;

import com.ringoid.Relationships;
import com.ringoid.ViewRelationshipSource;
import com.ringoid.events.internal.events.MessageEvent;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ringoid.BlockProperties.BLOCK_AT;
import static com.ringoid.BlockProperties.BLOCK_REASON_NUM;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.LikeProperties.LIKED_AT;
import static com.ringoid.LikeProperties.LIKE_COUNT;
import static com.ringoid.MessageProperties.MSG_AT;
import static com.ringoid.MessageProperties.MSG_COUNT;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.ViewProperties.VIEW_AT;
import static com.ringoid.ViewProperties.VIEW_COUNT;
import static com.ringoid.ViewProperties.VIEW_TIME_IN_SEC;
import static com.ringoid.common.UtilsInternaly.doWeHaveBlockInternaly;

public class ActionsUtilsInternaly {
    private static final int REPORT_REASON_TRESHOLD = 9;
    private static final String RELATIONSHIP_TYPE = "relationShipType";
    private static final String START_NODE_USER_ID = "startNodeUserId";
    private static final String END_NODE_USER_ID = "endNodeUserId";
    private static final String NUM = "num";

    private static final String REL_EXIST_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r]-(targetUser:%s {%s: $targetUserId}) " +
                            "RETURN type(r) as %s, startNode(r).userId as %s, endNode(r).userId as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    RELATIONSHIP_TYPE, START_NODE_USER_ID, END_NODE_USER_ID);

    private static final String LIKED_PHOTO_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (p:%s {%s: $photoId}) " +
                            "WHERE NOT (sourceUser)-[:%s]->(p) " +
                            "MERGE (sourceUser)-[photoRel:%s]->(p) " +
                            "ON CREATE SET photoRel.%s = $likeCount, photoRel.%s = $likedAt " +
                            "ON MATCH SET photoRel.%s = photoRel.%s + $likeCount",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.UPLOAD_PHOTO.name(),
                    Relationships.LIKE.name(),
                    LIKE_COUNT.getPropertyName(), LIKED_AT.getPropertyName(),
                    LIKE_COUNT.getPropertyName(), LIKE_COUNT.getPropertyName());

    private static final String LIKED_PROFILE_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (targetUser:%s {%s: $targetUserId}) " +
                            "WHERE sourceUser.%s <> targetUser.%s " +
                            "MERGE (sourceUser)-[profileRel:%s]->(targetUser) " +
                            "ON CREATE SET profileRel.%s = $likedAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    Relationships.LIKE.name(),
                    LIKED_AT.getPropertyName());

    private static final String UNLIKE_PHOTO_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]->(p:%s {%s: $photoId}) DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName()
            );

    private static final String HOW_MANY_PHOTO_LIKE_QUERY_PART_OF_UNLIKE =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]->(p:%s)<-[:%s]-(target:%s {%s: $targetUserId}) " +
                            "RETURN count(r) as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    NUM
            );

    private static final String REMOVE_LIKE_BETWEEN_PROFILE_PART_OF_UNLIKE =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r:%s]->(target:%s {%s: $targetUserId}) " +
                            "DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName()
            );

    private static final String CREATE_MATCH_AFTER_LIKE_QUERY =
            String.format("MATCH (source:%s {%s: $sourceUserId})<-[like:%s]-(target:%s {%s: $targetUserId}) " +
                            "DELETE like MERGE (source)-[:%s]-(target)",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    Relationships.MATCH.name());

    private static final String CREATE_MESSAGE_AFTER_LIKE_QUERY =
            String.format("MATCH (source:%s {%s: $sourceUserId})<-[like:%s]-(target:%s {%s: $targetUserId})-[upl:%s]->(p:%s {%s: $targetPhotoId}) " +
                            "DELETE like MERGE (source)-[msg:%s]-(target) " +
                            "ON CREATE SET msg.%s = $messageAt " +
                            "ON MATCH SET msg.%s = $messageAt " +
                            "WITH source, p " +
                            "MERGE (source)-[ms:%s]->(p) " +
                            "ON CREATE SET ms.%s = $msgCount " +
                            "ON MATCH SET ms.%s = ms.%s + $msgCount",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.MESSAGE.name(),
                    MSG_AT.getPropertyName(),
                    MSG_AT.getPropertyName(),
                    Relationships.MESSAGE.name(),
                    MSG_COUNT.getPropertyName(),
                    MSG_COUNT.getPropertyName(), MSG_COUNT.getPropertyName());

    private static final String CREATE_MESSAGE_AFTER_MATCH_QUERY =
            String.format("MATCH (source:%s {%s: $sourceUserId})-[mat:%s]-(target:%s {%s: $targetUserId})-[upl:%s]->(p:%s {%s: $targetPhotoId}) " +
                            "DELETE mat MERGE (source)-[msg:%s]-(target) " +
                            "ON CREATE SET msg.%s = $messageAt " +
                            "ON MATCH SET msg.%s = $messageAt " +
                            "WITH source, p " +
                            "MERGE (source)-[ms:%s]->(p) " +
                            "ON CREATE SET ms.%s = $msgCount " +
                            "ON MATCH SET ms.%s = ms.%s + $msgCount",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.MESSAGE.name(),
                    MSG_AT.getPropertyName(),
                    MSG_AT.getPropertyName(),
                    Relationships.MESSAGE.name(),
                    MSG_COUNT.getPropertyName(),
                    MSG_COUNT.getPropertyName(), MSG_COUNT.getPropertyName());

    private static final String CREATE_MESSAGE_AFTER_MESSAGE_QUERY =
            String.format("MATCH (source:%s {%s: $sourceUserId})-[mess:%s]-(target:%s {%s: $targetUserId})-[upl:%s]->(p:%s {%s: $targetPhotoId}) " +
                            "SET mess.%s = $messageAt " +
                            "MERGE (source)-[msg:%s]->(p) " +
                            "ON CREATE SET msg.%s = $msgCount " +
                            "ON MATCH SET msg.%s = msg.%s + $msgCount",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MESSAGE.name(), PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    MSG_AT.getPropertyName(),
                    Relationships.MESSAGE.name(),
                    MSG_COUNT.getPropertyName(),
                    MSG_COUNT.getPropertyName(), MSG_COUNT.getPropertyName());

    private static final String DELETE_ALL_INCOMING_PHOTO_RELATIONSHIPS_FROM_BLOCKED_PROFILE_QUERY =
            String.format("MATCH (source:%s {%s:$sourceUserId})-[:%s]->(ph:%s)<-[r]-(target:%s {%s: $targetUserId}) " +
                            "WHERE source.%s <> target.%s " +
                            "DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName()
            );

    private static final String DELETE_ALL_OUTGOUING_PHOTO_RELATIONSHIPS_WITH_BLOCKED_PROFILE_QUERY =
            String.format("MATCH (source:%s {%s:$sourceUserId})-[r]->(ph:%s)<-[:%s]-(target:%s {%s: $targetUserId}) " +
                            "WHERE source.%s <> target.%s " +
                            "DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName()
            );

    private static final String DELETE_ALL_RELATIONSHIPS_BETWEEN_PROFILES_QUERY =
            String.format("MATCH (source:%s {%s:$sourceUserId})-[r]-(target:%s {%s: $targetUserId}) " +
                            "WHERE source.%s <> target.%s " +
                            "DELETE r",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName()
            );

    private static final String CREATE_BLOCK_QUERY =
            String.format("MATCH (source:%s {%s:$sourceUserId}), (target:%s {%s: $targetUserId}) " +
                            "WHERE source.%s <> target.%s " +
                            "MERGE (source)-[r:%s]->(target) " +
                            "ON CREATE SET r.%s = $blockedAt, r.%s = $blockReasonNum " +
                            "WITH source " +
                            "MATCH (target:%s {%s: $targetUserId})-[:%s]->(targetPhoto:%s {%s: $targetPhotoId}) " +
                            "MERGE (source)-[r:%s]->(targetPhoto) " +
                            "ON CREATE SET r.%s = $blockedAt, r.%s = $blockReasonNum",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    Relationships.BLOCK.name(),
                    BLOCK_AT.getPropertyName(), BLOCK_REASON_NUM.getPropertyName(),

                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.BLOCK.name(),
                    BLOCK_AT.getPropertyName(), BLOCK_REASON_NUM.getPropertyName()
            );

    private static final String UPDATE_LAST_ACTION_TIME_QUERY =
            String.format(
                    "MATCH (source:%s {%s:$sourceUserId}) SET source.%s = $lastActionTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), LAST_ACTION_TIME.getPropertyName()
            );

    private static String deleteRelsQuery(Relationships... rels) {
        String resultStr = "";
        for (Relationships each : rels) {
            resultStr += each.name() + "|";
        }
        resultStr = resultStr.substring(0, resultStr.lastIndexOf("|"));
        return String.format(
                "MATCH (sourceUser:%s {%s: $sourceUserId})-[rel:%s]->(targetUser:%s {%s: $targetUserId}) " +
                        "DELETE rel",
                PERSON.getLabelName(), USER_ID.getPropertyName(),
                resultStr,
                PERSON.getLabelName(), USER_ID.getPropertyName()
        );
    }

    private static String viewQuery(UserViewPhotoEvent event) {
        ViewRelationshipSource source = ViewRelationshipSource.fromString(event.getSource());
        Relationships targetPhotoRelationship = Relationships.VIEW;
        Relationships targetProfileRelationship;
        switch (source) {
            case NEW_FACES:
                targetProfileRelationship = Relationships.VIEW;
                break;
            case WHO_LIKED_ME:
                targetProfileRelationship = Relationships.VIEW_IN_LIKES_YOU;
                break;
            case MATCHES:
                targetProfileRelationship = Relationships.VIEW_IN_MATCHES;
                break;
            case MESSAGES:
                targetProfileRelationship = Relationships.VIEW_IN_MESSAGES;
                break;
            case CHAT:
                //todo:discuss this
                targetProfileRelationship = Relationships.VIEW;
                break;
            default:
                throw new IllegalArgumentException("Unsupported source " + source.getValue());
        }

        return String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (p:%s {%s: $photoId}), (targetUser:%s {%s: $targetUserId}) " +
                        "WHERE sourceUser.%s <> targetUser.%s AND (targetUser)-[:%s]->(p) " +
                        "MERGE (sourceUser)-[photoRel:%s]->(p) " +
                        "ON CREATE SET photoRel.%s = $viewCount, photoRel.%s = $viewTimeSec, photoRel.%s = $viewAt " +
                        "ON MATCH SET photoRel.%s = photoRel.%s + $viewCount, photoRel.%s = photoRel.%s + $viewTimeSec " +
                        "MERGE (sourceUser)-[profileRel:%s]->(targetUser) " +
                        "ON CREATE SET profileRel.%s = $viewAt",
                PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                USER_ID.getPropertyName(), USER_ID.getPropertyName(), Relationships.UPLOAD_PHOTO.name(),

                targetPhotoRelationship.name(),
                VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_AT.getPropertyName(),
                VIEW_COUNT.getPropertyName(), VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(),
                targetProfileRelationship.name(),
                VIEW_AT.getPropertyName()
        );
    }

    public static void messageInternal(UserMessageEvent event, GraphDatabaseService database) {

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("targetPhotoId", event.getOriginPhotoId());
        parameters.put("messageAt", event.getMessageAt());
        parameters.put("lastActionTime", event.getMessageAt());
        parameters.put("msgCount", 1);

        database.execute(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

        if (doWeHaveBlockInternaly(event.getUserId(), event.getTargetUserId(), database)) {
//            log.warn("BLOCK exist between source userId {} and target userId {}, can not message",
//                    event.getUserId(), event.getTargetUserId());
            return;
        }

        Map<String, Object> map = getAllRelInternal(database, parameters, event.getUserId(), event.getTargetUserId());
        Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");
        if (!existRelationshipsBetweenProfiles.contains(Relationships.LIKE) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.MATCH) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
//            log.warn("LIKE, MATCH or MESSAGE does not exist between source userId {} and target userId {}, can not message",
//                    event.getUserId(), event.getTargetUserId());
            return;
        }


        existRelationshipsBetweenProfiles.remove(Relationships.VIEW);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_LIKES_YOU);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MATCHES);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MESSAGES);

        MessageEvent messageEvent = new MessageEvent(event.getUserId(), event.getTargetUserId(),
                event.getText(), event.getUnixTime(), event.getMessageAt());

        //if message between profiles already exist just update time and send it
        if (existRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
//            log.debug("MESSAGE already exist between source userId {} and target userId {}, send message",
//                    event.getUserId(), event.getTargetUserId());
            Result result = database.execute(CREATE_MESSAGE_AFTER_MESSAGE_QUERY, parameters);
            int relCreated = result.getQueryStatistics().getRelationshipsCreated();
//            log.info("{} message relationships were created for source userId {} to target userId {} and target photoId {}",
//                    counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            //todo:implement
//            Utils.sendEventIntoInternalQueue(messageEvent, kinesis, streamName, event.getUserId(), gson);

            //send bot event
//            sendBotEvent(event.botEvent(), sqs, sqsUrl, botEnabled, gson);

            return;
        }

        if (existRelationshipsBetweenProfiles.contains(Relationships.MATCH)) {
            Result result = database.execute(CREATE_MESSAGE_AFTER_MATCH_QUERY, parameters);
            int relCreated = result.getQueryStatistics().getRelationshipsCreated();

//            log.info("{} message relationships were created for source userId {} to target userId {} and target photoId {}",
//                    counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            //todo:implement
//            Utils.sendEventIntoInternalQueue(messageEvent, kinesis, streamName, event.getUserId(), gson);
            //send bot event
//            sendBotEvent(event.botEvent(), sqs, sqsUrl, botEnabled, gson);

            return;
        }

        //now check that user has right like
        //first check that it's opposite like
        String startNode = null;
        List<Map<String, Object>> recordList = (List<Map<String, Object>>) map.get("list");
        for (Map<String, Object> each : recordList) {
            if (Objects.equals((String) each.get(RELATIONSHIP_TYPE), Relationships.LIKE.name())) {
                startNode = (String) each.get(START_NODE_USER_ID);
            }
        }

        if (Objects.equals(startNode, event.getUserId())) {
//            log.warn("there is a like between source profile userId {} and target profile userId {}, but wrong direction, can not create message",
//                    event.getUserId(), event.getTargetUserId());
            return;
        }

        //match here, but it's already message !!!
        //todo:don't forget that it's the same match, m.b. need to implement some kind of notification later
        Result result = database.execute(CREATE_MESSAGE_AFTER_LIKE_QUERY, parameters);
        int relCreated = result.getQueryStatistics().getRelationshipsCreated();
        if (relCreated > 0) {
            //todo:implement
//            Utils.sendEventIntoInternalQueue(messageEvent, kinesis, streamName, event.getUserId(), gson);
            //send bot event
//            sendBotEvent(event.botEvent(), sqs, sqsUrl, botEnabled, gson);
        }
//        log.info("{} message relationships were created between source profile userId {}, target profile userId {} and target photoId {}",
//                counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());

        return;
    }

    public static void unlikeInternal(UserUnlikePhotoEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("lastActionTime", event.getUnLikedAt());

        database.execute(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

        if (doWeHaveBlockInternaly(event.getUserId(), event.getTargetUserId(), database)) {
//            log.warn("BLOCK exist between source userId {} and target userId {}, can not unlike photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }

        Map<String, Object> map = getAllRelInternal(database, parameters, event.getUserId(), event.getTargetUserId());
        Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");
        if (!existRelationshipsBetweenProfiles.contains(Relationships.VIEW) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
//            log.warn("VIEW does not exist between source userId {} and target userId {}, can not unlike photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }

        Result result = database.execute(UNLIKE_PHOTO_QUERY, parameters);
        int relDeleted = result.getQueryStatistics().getRelationshipsDeleted();
//        log.info("{} photo like relationships were deleted from userId {} to photoId {}",
//                counters.relationshipsDeleted(), event.getUserId(), event.getOriginPhotoId());

        result = database.execute(HOW_MANY_PHOTO_LIKE_QUERY_PART_OF_UNLIKE, parameters);
//        result = tx.run(HOW_MANY_PHOTO_LIKE_QUERY_PART_OF_UNLIKE, parameters);

        int num = (Integer) result.next().get(NUM);

//        log.info("{} like relationships exist between userId {} and photo's of target userId {}",
//                num, event.getUserId(), event.getTargetUserId());
        if (num == 0) {
            result = database.execute(REMOVE_LIKE_BETWEEN_PROFILE_PART_OF_UNLIKE, parameters);
            int relDel = result.getQueryStatistics().getRelationshipsDeleted();
//            log.info("{} like relationships were deleted between source profile userId {} and target profile userId {}",
//                    counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
        }
        return;
    }

    public static void blockInternaly(UserBlockOtherEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("blockedAt", event.getBlockedAt());
        parameters.put("lastActionTime", event.getBlockedAt());
        parameters.put("blockReasonNum", event.getBlockReasonNum());
        parameters.put("targetPhotoId", event.getOriginPhotoId());

        database.execute(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

        if (doWeHaveBlockInternaly(event.getUserId(), event.getTargetUserId(), database)) {
//            log.warn("BLOCK already exist between source userId {} and target userId {}, can not block",
//                    event.getUserId(), event.getTargetUserId());
            return;
        }

        //todo:future place for optimization (can ask only about VIEW rel)
        Map<String, Object> map = getAllRelInternal(database, parameters, event.getUserId(), event.getTargetUserId());
        Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");
        if (!existRelationshipsBetweenProfiles.contains(Relationships.VIEW) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
//            log.warn("VIEW does not exist between source userId {} and target userId {}, can not block photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }


        Result result = database.execute(DELETE_ALL_INCOMING_PHOTO_RELATIONSHIPS_FROM_BLOCKED_PROFILE_QUERY, parameters);
        int relDeleted = result.getQueryStatistics().getRelationshipsDeleted();
//        log.info("{} relationships were deleted between source user's photo with userId {} and blocked userId {}",
//                counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
        result = database.execute(DELETE_ALL_OUTGOUING_PHOTO_RELATIONSHIPS_WITH_BLOCKED_PROFILE_QUERY, parameters);
        relDeleted = result.getQueryStatistics().getRelationshipsDeleted();
//        log.info("{} relationships were deleted between blocked user's photo with userId {} and source userId {}",
//                counters.relationshipsDeleted(), event.getTargetUserId(), event.getUserId());
        result = database.execute(DELETE_ALL_RELATIONSHIPS_BETWEEN_PROFILES_QUERY, parameters);
        relDeleted = result.getQueryStatistics().getRelationshipsDeleted();
//        log.info("{} relationships were deleted between source profile userId {} and target profile userId {}",
//                counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
        result = database.execute(CREATE_BLOCK_QUERY, parameters);
        int relCreated = result.getQueryStatistics().getRelationshipsCreated();
//        log.info("{} block relationships were created between source profile userId {}, target profile userId {} and target photoId {}",
//                counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId(), event.getTargetPhotoId());

        //currently users couldn't block itself concurrently (only first block will be applied
        if (event.getBlockReasonNum() > REPORT_REASON_TRESHOLD && relCreated > 0) {
            //todo:implement
//            Utils.markPersonForModeration(event.getTargetUserId(), tx);
//            Utils.sendEventIntoInternalQueue(event, kinesis, streamName, event.getTargetUserId(), gson);
            return;
        }

        //if it's just block - we can delete the conversation
//        DeleteUserConversationEvent deleteUserConversationEvent = new DeleteUserConversationEvent(event.getUserId(), event.getTargetUserId());
//        Utils.sendEventIntoInternalQueue(deleteUserConversationEvent, kinesis, streamName, deleteUserConversationEvent.getUserId(), gson);
        return;
    }

    public static void viewPhotoInternaly(UserViewPhotoEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("viewCount", event.getViewCount());
        parameters.put("viewTimeSec", event.getViewTimeMillis());
        parameters.put("viewAt", event.getViewAt());
        parameters.put("lastActionTime", event.getViewAt());

        database.execute(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

        if (doWeHaveBlockInternaly(event.getUserId(), event.getTargetUserId(), database)) {
//            log.warn("BLOCK exist between source userId {} and target userId {}, can not view photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }


        Result result = database.execute(viewQuery(event), parameters);
        int relCreated = result.getQueryStatistics().getRelationshipsCreated();
//        log.info("{} view relationships were created from source userId {} to photoId {} and target userId {}",
//                counters.relationshipsCreated(), event.getUserId(), event.getOriginPhotoId(), event.getTargetUserId());

        Map<String, Object> map = getAllRelInternal(database, parameters, event.getUserId(), event.getTargetUserId());
        Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");

        String deleteOtherViewsQuery = null;
        if (existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES)) {
            deleteOtherViewsQuery = deleteRelsQuery(Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES);
        } else if (existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
            deleteOtherViewsQuery = deleteRelsQuery(Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU);
        } else if (existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU)) {
            deleteOtherViewsQuery = deleteRelsQuery(Relationships.VIEW);
        }

        if (deleteOtherViewsQuery != null) {
            result = database.execute(deleteOtherViewsQuery, parameters);
            int relDeleted = result.getQueryStatistics().getRelationshipsDeleted();
//            log.info("{} parent view relationships were deleted from source userId {} and target userId {}",
//                    counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
        }
        return;
    }

    public static void likePhotoInternaly(UserLikePhotoEvent event, GraphDatabaseService database) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("likeCount", event.getLikeCount());
        parameters.put("likedAt", event.getLikedAt());
        parameters.put("lastActionTime", event.getLikedAt());

        database.execute(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

        Map<String, Object> map = getAllRelInternal(database, parameters, event.getUserId(), event.getTargetUserId());
        Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");
        if (!existRelationshipsBetweenProfiles.contains(Relationships.VIEW) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES) &&
                !existRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
//            log.warn("VIEW does not exist between source userId {} and target userId {}, can not like photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_LIKES_YOU);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MATCHES);
        existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MESSAGES);

        if (existRelationshipsBetweenProfiles.contains(Relationships.BLOCK)) {
//            log.warn("BLOCK exist between source userId {} and target userId {}, can not like photo {}",
//                    event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
            return;
        }

        //there is no block, so we can like a photo
        Result result = database.execute(LIKED_PHOTO_QUERY, parameters);
        if (result.getQueryStatistics().getRelationshipsCreated() <= 0) {
//            log.info("0 photo like relationships were created from userId {} to photoId {}",
//                    event.getUserId(), event.getOriginPhotoId());
            return;
        }

//        log.info("{} photo like relationships were created from userId {} to photoId {}",
//                counters.relationshipsCreated(), event.getUserId(), event.getOriginPhotoId());

        //todo:send invocation
//        PhotoLikeEvent likeEvent = new PhotoLikeEvent(event.getTargetUserId(), event.getOriginPhotoId());
//        Utils.sendEventIntoInternalQueue(likeEvent, kinesis, streamName, event.getTargetUserId(), gson);

        //send bot event
//        sendBotEvent(event.botUserLikePhotoEvent(), sqs, sqsUrl, botEnabled, gson);

        //now check which type of relationships we should create between profiles
        //if there is a match already or a message then we should return
        if (existRelationshipsBetweenProfiles.contains(Relationships.MATCH) ||
                existRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
//            log.info("there is a match or message between source userId {} and target userId {} " +
//                            "so skip relationship creation between profiles",
//                    event.getUserId(), event.getTargetUserId());
            return;
        }

        //if there is no any relationships except view of any kind, then create a like
        if (existRelationshipsBetweenProfiles.isEmpty()) {
            result = database.execute(LIKED_PROFILE_QUERY, parameters);
            int counters = result.getQueryStatistics().getRelationshipsCreated();
//            log.info("{} profile like relationships were created for source userId {} to target userId {}",
//                    counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());
            return;
        }

        //now check the difficult case
        if (existRelationshipsBetweenProfiles.contains(Relationships.LIKE)) {
            //first check that it's opposite like
            String startNode = null;
            List<Map<String, Object>> recordList = (List<Map<String, Object>>) map.get("list");
            for (Map<String, Object> each : recordList) {
                if (Objects.equals((String) each.get(RELATIONSHIP_TYPE), Relationships.LIKE.name())) {
                    startNode = (String) each.get(START_NODE_USER_ID);
                }
            }

            if (Objects.equals(startNode, event.getUserId())) {
//                log.info("there is a like already between source profile userId {} and target profile userId {}",
//                        event.getUserId(), event.getTargetUserId());
                return;
            }
//            log.info("there is a like between target profile userId {} and source profile userId {}, so create a match",
//                    event.getTargetUserId(), event.getUserId());

            //match here !!!
            result = database.execute(CREATE_MATCH_AFTER_LIKE_QUERY, parameters);
            if (result.getQueryStatistics().getRelationshipsCreated() > 0) {
                //todo:send match
                //sendEventIntoInternalQueue(event);
            }
//            log.info("{} match relationships were created between source profile userId {} and target profile userId {}",
//                    counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());
            return;
        }
        return;
    }

    private static Map<String, Object> getAllRelInternal(GraphDatabaseService database, Map<String, Object> parameters,
                                                         String userId, String targetUserId) {
        Map<String, Object> map = new HashMap<>();
        Set<Relationships> existRelationshipsBetweenProfiles = new HashSet<>();
        Result result = database.execute(REL_EXIST_QUERY, parameters);
        List<Map<String, Object>> recordList = new ArrayList<>();
        while (result.hasNext()) {
            Map<String, Object> each = result.next();
            recordList.add(each);
            String rel = (String) each.get(RELATIONSHIP_TYPE);
            Relationships relationship = Relationships.fromString(rel);
            if (relationship == Relationships.UNSUPPORTED) {
                //todo:mb comment
            } else {
                existRelationshipsBetweenProfiles.add(relationship);
            }
        }
        map.put("set", existRelationshipsBetweenProfiles);
        map.put("list", recordList);
        return map;
    }
//    private static void sendBotEvent(Object event, AmazonSQS sqs, String sqsUrl, boolean botEnabled, Gson gson) {
//        log.debug("try to send bot event {}, botEnabled {}", event, botEnabled);
//        if (!botEnabled) {
//            return;
//        }
//        log.debug("send bot event {} into sqs queue", event);
//        sqs.sendMessage(sqsUrl, gson.toJson(event));
//        log.debug("successfully send bot event {} into sqs queue", event);
//    }

}
