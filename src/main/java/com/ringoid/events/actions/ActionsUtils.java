package com.ringoid.events.actions;

import com.ringoid.Relationships;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;
import org.neo4j.driver.v1.TransactionWork;
import org.neo4j.driver.v1.summary.SummaryCounters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.LikeProperties.LIKED_AT;
import static com.ringoid.LikeProperties.LIKE_COUNT;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.ViewProperties.VIEW_AT;
import static com.ringoid.ViewProperties.VIEW_COUNT;
import static com.ringoid.ViewProperties.VIEW_TIME_IN_SEC;

public class ActionsUtils {
    private static final Logger log = LoggerFactory.getLogger(ActionsUtils.class);

    private static final String RELATIONSHIP_TYPE = "relationShipType";
    private static final String START_NODE_USER_ID = "startNodeUserId";
    private static final String END_NODE_USER_ID = "endNodeUserId";

    private static final String REL_EXIST_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId})-[r]-(targetUser:%s {%s: $targetUserId}) " +
                            "RETURN type(r) as %s, startNode(r).userId as %s, endNode(r).userId as %s",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    RELATIONSHIP_TYPE, START_NODE_USER_ID, END_NODE_USER_ID);

    private static final String LIKED_PHOTO_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (p:%s {%s: $photoId}) " +
                            "MERGE (sourceUser)-[photoRel:%s]->(p) " +
                            "ON CREATE SET photoRel.%s = $likeCount, photoRel.%s = $likedAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(),
                    Relationships.LIKE.name(),
                    LIKE_COUNT.getPropertyName(), LIKED_AT.getPropertyName());

    private static final String LIKED_PROFILE_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (targetUser:%s {%s: $targetUserId}) " +
                            "MERGE (sourceUser)-[profileRel:%s]->(targetUser) " +
                            "ON CREATE SET profileRel.%s = $likedAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    Relationships.LIKE.name(),
                    LIKED_AT.getPropertyName());

    private static final String CREATE_MATCH =
            String.format("MATCH (source:%s {%s: $sourceUserId})<-[like:%s]-(target:%s {%s: $targetUserId}) " +
                            "DELETE like MERGE (source)-[:MATCH]-(target)",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), USER_ID.getPropertyName());

    private static final String VIEW_QUERY =
            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (p:%s {%s: $photoId}), (targetUser:%s {%s: $targetUserId}) " +
                            "MERGE (sourceUser)-[photoRel:%s]->(p) " +
                            "ON CREATE SET photoRel.%s = $viewCount, photoRel.%s = $viewTimeSec, photoRel.%s = $viewAt " +
                            "ON MATCH SET photoRel.%s = photoRel.%s + $viewCount, photoRel.%s = photoRel.%s + $viewTimeSec " +
                            "MERGE (sourceUser)-[profileRel:%s]->(targetUser) " +
                            "ON CREATE SET profileRel.%s = $viewAt",
                    PERSON.getLabelName(), USER_ID.getPropertyName(), PHOTO.getLabelName(), PHOTO_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
                    Relationships.VIEW.name(),
                    VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_AT.getPropertyName(),
                    VIEW_COUNT.getPropertyName(), VIEW_COUNT.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(), VIEW_TIME_IN_SEC.getPropertyName(),
                    Relationships.VIEW.name(),
                    VIEW_AT.getPropertyName());

    //    private static final String VIEW_PROFILE_QUERY =
//            String.format("MATCH (sourceUser:%s {%s: $sourceUserId}), (targetUser:%s {%s: $targetUserId}) " +
//                            "MERGE (sourceUser)-[profileRel:%s]->(targetUser) " +
//                            "ON CREATE SET profileRel.%s = $viewAt",
//                    PERSON.getLabelName(), USER_ID.getPropertyName(), PERSON.getLabelName(), USER_ID.getPropertyName(),
//                    Relationships.VIEW.name(),
//                    VIEW_AT.getPropertyName());
//
    public static void viewPhoto(UserViewPhotoEvent event, Driver driver) {
        log.debug("view photo event {}", event);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("viewCount", event.getViewCount());
        parameters.put("viewTimeSec", event.getViewTimeSec());
        parameters.put("viewAt", event.getViewAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    StatementResult result = tx.run(VIEW_QUERY, parameters);
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
        log.info("successfully view photo {}", event);
    }

    public static void likePhoto(UserLikePhotoEvent event, Driver driver) {
        log.debug("like photo {}", event);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("sourceUserId", event.getUserId());
        parameters.put("photoId", event.getOriginPhotoId());
        parameters.put("targetUserId", event.getTargetUserId());
        parameters.put("likeCount", event.getLikeCount());
        parameters.put("likedAt", event.getLikedAt());

        try (Session session = driver.session()) {
            session.writeTransaction(new TransactionWork<Integer>() {
                @Override
                public Integer execute(Transaction tx) {
                    Set<Relationships> existRelationshipsBetweenProfiles = new HashSet<>();
                    StatementResult result = tx.run(REL_EXIST_QUERY, parameters);
                    List<Record> recordList = result.list();
                    for (Record each : recordList) {
                        String rel = each.get(RELATIONSHIP_TYPE).asString();
                        Relationships relationship = Relationships.fromString(rel);
                        if (relationship == Relationships.UNSUPPORTED) {
                            log.warn("found unsupported relationship type {} between source userId {} and target userId {}",
                                    rel, event.getUserId(), event.getTargetUserId());
                        } else {
                            existRelationshipsBetweenProfiles.add(relationship);
                        }
                    }
                    existRelationshipsBetweenProfiles.remove(Relationships.VIEW);

                    if (existRelationshipsBetweenProfiles.contains(Relationships.BLOCK)) {
                        log.warn("BLOCK exist between source userId {} and target userId {}, can not like photo {}",
                                event.getUserId(), event.getTargetUserId(), event.getOriginPhotoId());
                        return 1;
                    }

                    //there is no block, so we can like a photo
                    result = tx.run(LIKED_PHOTO_QUERY, parameters);
                    SummaryCounters counters = result.summary().counters();
                    if (counters.relationshipsCreated() > 0) {
                        sendEventIntoInternalQueue(event);
                    }
                    log.info("{} photo like relationships were created from userId {} to photoId {}",
                            counters.relationshipsCreated(), event.getUserId(), event.getOriginPhotoId());

                    //now check which type of relationships we should create between profiles
                    //if there is a match already or a message then we should return
                    if (existRelationshipsBetweenProfiles.contains(Relationships.MATCH) ||
                            existRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
                        log.debug("there is a match or message between source userId {} and target userId {} " +
                                        "so skip relationship creation between profiles",
                                event.getUserId(), event.getTargetUserId());
                        return 1;
                    }

                    //if there is no any relationships except view, then create a like
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
                        String endNode = null;
                        for (Record each : recordList) {
                            if (Objects.equals(each.get(RELATIONSHIP_TYPE).asString(), Relationships.LIKE.name())) {
                                startNode = each.get(START_NODE_USER_ID).asString();
                                endNode = each.get(END_NODE_USER_ID).asString();
                            }
                        }

                        if (Objects.equals(startNode, event.getUserId())) {
                            log.debug("there is a like already between source profile userId {} and target profile userId {}",
                                    event.getUserId(), event.getTargetUserId());
                            return 1;
                        }
                        log.debug("there is a like between target profile userId {} and source profile userId {}, so create a match",
                                event.getTargetUserId(), event.getUserId());

                        //match here !!!
                        result = tx.run(CREATE_MATCH, parameters);
                        counters = result.summary().counters();
                        if (counters.relationshipsCreated() > 0) {
                            sendEventIntoInternalQueue(event);
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
        log.info("successfully like photo {}", event);
    }

    private static void sendEventIntoInternalQueue(Object event) {
        log.debug("send event {} into internal queue", event);
        //todo:implement
        log.debug("successfully send event {} into internal queue", event);
    }

}