package com.ringoid.events.actions;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.PutRecordRequest;
import com.google.gson.Gson;
import com.ringoid.Relationships;
import com.ringoid.ViewRelationshipSource;
import com.ringoid.events.internal.events.PhotoLikeEvent;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.driver.v1.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ringoid.BlockProperties.BLOCK_AT;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.LikeProperties.LIKED_AT;
import static com.ringoid.LikeProperties.LIKE_COUNT;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.ViewProperties.VIEW_AT;
import static com.ringoid.ViewProperties.VIEW_COUNT;
import static com.ringoid.ViewProperties.VIEW_TIME_IN_SEC;
import static com.ringoid.common.Utils.doWeHaveBlock;

public class ActionsUtils {
    private static final Logger log = LoggerFactory.getLogger(ActionsUtils.class);

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
                            "ON CREATE SET photoRel.%s = $likeCount, photoRel.%s = $likedAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.UPLOAD_PHOTO.name(),
                    Relationships.LIKE.name(),
                    LIKE_COUNT.getPropertyName(), LIKED_AT.getPropertyName());

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

    private static final String CREATE_MATCH_QUERY =
            String.format("MATCH (source:%s {%s: $sourceUserId})<-[like:%s]-(target:%s {%s: $targetUserId}) " +
                            "DELETE like MERGE (source)-[:MATCH]-(target)",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName());

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
                            "ON CREATE SET r.%s = $blockedAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    USER_ID.getPropertyName(), USER_ID.getPropertyName(),
                    Relationships.BLOCK.name(),
                    BLOCK_AT.getPropertyName()
            );

    private static final String UPDATE_LAST_ACTION_TIME_QUERY =
            String.format(
                    "MATCH (source:%s {%s:$sourceUserId}) SET source.%s = $lastActionTime",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), LAST_ACTION_TIME.getPropertyName()
            );


    private static String viewQuery(UserViewPhotoEvent event) {
        ViewRelationshipSource source = ViewRelationshipSource.fromString(event.getSource());
        Relationships targetRelationship;
        switch (source) {
            case NEW_FACES:
                targetRelationship = Relationships.VIEW;
                break;
            case WHO_LIKED_ME:
                targetRelationship = Relationships.VIEW_IN_LIKES_YOU;
                break;
            case MATCHES:
                targetRelationship = Relationships.VIEW_IN_MATCHES;
                break;
            case MESSAGES:
                targetRelationship = Relationships.VIEW_IN_MESSAGES;
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
                targetRelationship.name(),
                VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_AT.getPropertyName(),
                VIEW_COUNT.getPropertyName(), VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(),
                targetRelationship.name(),
                VIEW_AT.getPropertyName()
        );
    }

    public static void unlike(UserUnlikePhotoEvent event, Driver driver) {
        log.debug("unlike photo event {} for userId {}", event, event.getUserId());
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("lastActionTime", event.getUnLikedAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

                    if (doWeHaveBlock(event.getUserId(), event.getTargetUserId(), tx)) {
                        log.warn("BLOCK exist between source userId {} and target userId {}, can not unlike photo {}",
                                event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
                        return 1;
                    }

                    StatementResult result = tx.run(UNLIKE_PHOTO_QUERY, parameters);
                    SummaryCounters counters = result.summary().counters();
                    log.info("{} photo like relationships were deleted from userId {} to photoId {}",
                            counters.relationshipsDeleted(), event.getUserId(), event.getOriginPhotoId());

                    result = tx.run(HOW_MANY_PHOTO_LIKE_QUERY_PART_OF_UNLIKE, parameters);
                    List<Record> recordList = result.list();
                    Record record = recordList.get(0);
                    int num = record.get(NUM).asInt();
                    log.info("{} like relationships exist between userId {} and photo's of target userId {}",
                            num, event.getUserId(), event.getTargetUserId());
                    if (num == 0) {
                        result = tx.run(REMOVE_LIKE_BETWEEN_PROFILE_PART_OF_UNLIKE, parameters);
                        counters = result.summary().counters();
                        log.info("{} like relationships were deleted between source profile userId {} and target profile userId {}",
                                counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
                    }
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error like photo {} for userId {}", event, event.getUserId(), throwable);
            throw throwable;
        }
    }

    public static void block(UserBlockOtherEvent event, Driver driver) {
        log.debug("block user event {} for userId {}", event, event.getUserId());
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("blockedAt", event.getBlockedAt());
        parameters.put("lastActionTime", event.getBlockedAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

                    if (doWeHaveBlock(event.getUserId(), event.getTargetUserId(), tx)) {
                        log.warn("BLOCK already exist between source userId {} and target userId {}, can not block",
                                event.getUserId(), event.getTargetUserId());
                        return 1;
                    }

                    StatementResult result = tx.run(DELETE_ALL_INCOMING_PHOTO_RELATIONSHIPS_FROM_BLOCKED_PROFILE_QUERY, parameters);
                    SummaryCounters counters = result.summary().counters();
                    log.info("{} relationships were deleted between source user's photo with userId {} and blocked userId {}",
                            counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
                    result = tx.run(DELETE_ALL_OUTGOUING_PHOTO_RELATIONSHIPS_WITH_BLOCKED_PROFILE_QUERY, parameters);
                    counters = result.summary().counters();
                    log.info("{} relationships were deleted between blocked user's photo with userId {} and source userId {}",
                            counters.relationshipsDeleted(), event.getTargetUserId(), event.getUserId());
                    result = tx.run(DELETE_ALL_RELATIONSHIPS_BETWEEN_PROFILES_QUERY, parameters);
                    counters = result.summary().counters();
                    log.info("{} relationships were deleted between source profile userId {} and target profile userId {}",
                            counters.relationshipsDeleted(), event.getUserId(), event.getTargetUserId());
                    result = tx.run(CREATE_BLOCK_QUERY, parameters);
                    counters = result.summary().counters();
                    log.info("{} block relationships were created between source profile userId {} and target profile userId {}",
                            counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error block user event {} for userId {}", event, event.getUserId(), throwable);
            throw throwable;
        }
    }

    public static void viewPhoto(UserViewPhotoEvent event, Driver driver) {
        log.debug("view photo event {} for userId {}", event, event.getUserId());
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("viewCount", event.getViewCount());
        parameters.put("viewTimeSec", event.getViewTimeSec());
        parameters.put("viewAt", event.getViewAt());
        parameters.put("lastActionTime", event.getViewAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

                    if (doWeHaveBlock(event.getUserId(), event.getTargetUserId(), tx)) {
                        log.warn("BLOCK exist between source userId {} and target userId {}, can not view photo {}",
                                event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
                        return 1;
                    }

                    StatementResult result = tx.run(viewQuery(event), parameters);
                    SummaryCounters counters = result.summary().counters();
                    log.info("{} view relationships were created from source userId {} to photoId {} and target userId {}",
                            counters.relationshipsCreated(), event.getUserId(), event.getOriginPhotoId(), event.getTargetUserId());
                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error view photo {}", event, throwable);
            throw throwable;
        }
    }

    public static void likePhoto(UserLikePhotoEvent event, Driver driver,
                                 AmazonKinesis kinesis, String streamName, Gson gson) {
        log.debug("like photo event {}", event);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("likeCount", event.getLikeCount());
        parameters.put("likedAt", event.getLikedAt());
        parameters.put("lastActionTime", event.getLikedAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    tx.run(UPDATE_LAST_ACTION_TIME_QUERY, parameters);

                    Map<String, Object> map = getAllRel(tx, parameters, event.getUserId(), event.getTargetUserId());
                    Set<Relationships> existRelationshipsBetweenProfiles = (Set<Relationships>) map.get("set");
                    existRelationshipsBetweenProfiles.remove(Relationships.VIEW);
                    existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_LIKES_YOU);
                    existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MATCHES);
                    existRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MESSAGES);

                    if (existRelationshipsBetweenProfiles.contains(Relationships.BLOCK)) {
                        log.warn("BLOCK exist between source userId {} and target userId {}, can not like photo {}",
                                event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
                        return 1;
                    }

                    //there is no block, so we can like a photo
                    StatementResult result = tx.run(LIKED_PHOTO_QUERY, parameters);
                    SummaryCounters counters = result.summary().counters();
                    if (counters.relationshipsCreated() <= 0) {
                        log.info("0 photo like relationships were created from userId {} to photoId {}",
                                event.getUserId(), event.getOriginPhotoId());
                        return 1;
                    }

                    log.info("{} photo like relationships were created from userId {} to photoId {}",
                            counters.relationshipsCreated(), event.getUserId(), event.getOriginPhotoId());

                    PhotoLikeEvent likeEvent = new PhotoLikeEvent(event.getTargetUserId(), event.getOriginPhotoId());
                    sendEventIntoInternalQueue(likeEvent, kinesis, streamName, event.getTargetUserId(), gson);

                    //now check which type of relationships we should create between profiles
                    //if there is a match already or a message then we should return
                    if (existRelationshipsBetweenProfiles.contains(Relationships.MATCH) ||
                            existRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
                        log.info("there is a match or message between source userId {} and target userId {} " +
                                        "so skip relationship creation between profiles",
                                event.getUserId(), event.getTargetUserId());
                        return 1;
                    }

                    //if there is no any relationships except view of any kind, then create a like
                    if (existRelationshipsBetweenProfiles.isEmpty()) {
                        result = tx.run(LIKED_PROFILE_QUERY, parameters);
                        counters = result.summary().counters();
                        log.info("{} profile like relationships were created for source userId {} to target userId {}",
                                counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());
                        return 1;
                    }

                    //now check the difficult case
                    if (existRelationshipsBetweenProfiles.contains(Relationships.LIKE)) {
                        //first check that it's opposite like
                        String startNode = null;
                        List<Record> recordList = (List<Record>) map.get("list");
                        for (Record each : recordList) {
                            if (Objects.equals(each.get(RELATIONSHIP_TYPE).asString(), Relationships.LIKE.name())) {
                                startNode = each.get(START_NODE_USER_ID).asString();
                            }
                        }

                        if (Objects.equals(startNode, event.getUserId())) {
                            log.info("there is a like already between source profile userId {} and target profile userId {}",
                                    event.getUserId(), event.getTargetUserId());
                            return 1;
                        }
                        log.info("there is a like between target profile userId {} and source profile userId {}, so create a match",
                                event.getTargetUserId(), event.getUserId());

                        //match here !!!
                        result = tx.run(CREATE_MATCH_QUERY, parameters);
                        counters = result.summary().counters();
                        if (counters.relationshipsCreated() > 0) {
                            //todo:send match
                            //sendEventIntoInternalQueue(event);
                        }
                        log.info("{} match relationships were created between source profile userId {} and target profile userId {}",
                                counters.relationshipsCreated(), event.getUserId(), event.getTargetUserId());

                        return 1;
                    }

                    return 1;
                }
            });
        } catch (Throwable throwable) {
            log.error("error like photo {}", event, throwable);
            throw throwable;
        }
    }

    private static Map<String, Object> getAllRel(Transaction tx, Map<String, Object> parameters,
                                                 String userId, String targetUserId) {
        Map<String, Object> map = new HashMap<>();
        Set<Relationships> existRelationshipsBetweenProfiles = new HashSet<>();
        StatementResult result = tx.run(REL_EXIST_QUERY, parameters);
        List<Record> recordList = result.list();
        for (Record each : recordList) {
            String rel = each.get(RELATIONSHIP_TYPE).asString();
            Relationships relationship = Relationships.fromString(rel);
            if (relationship == Relationships.UNSUPPORTED) {
                log.warn("found unsupported relationship type {} between source userId {} and target userId {}",
                        rel, userId, targetUserId);
            } else {
                existRelationshipsBetweenProfiles.add(relationship);
            }
        }
        map.put("set", existRelationshipsBetweenProfiles);
        map.put("list", recordList);
        return map;
    }

    private static void sendEventIntoInternalQueue(Object event,
                                                   AmazonKinesis kinesis, String streamName, String partitionKey,
                                                   Gson gson) {
        log.debug("send event {} into internal kinesis queue", event);
        PutRecordRequest putRecordRequest = new PutRecordRequest();
        putRecordRequest.setStreamName(streamName);
        String strRep = gson.toJson(event);
        putRecordRequest.setData(ByteBuffer.wrap(strRep.getBytes()));
        putRecordRequest.setPartitionKey(partitionKey);
        kinesis.putRecord(putRecordRequest);
        log.debug("successfully send event {} into internal queue", event);
    }

}