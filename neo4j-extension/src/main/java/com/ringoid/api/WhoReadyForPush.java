package com.ringoid.api;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.LikeProperties;
import com.ringoid.MatchProperties;
import com.ringoid.MessageRelationshipProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.CREATED;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.PUSH_WAS_SENT_AT;
import static com.ringoid.PersonProperties.SETTINGS_LOCALE;
import static com.ringoid.PersonProperties.SETTINGS_PUSH;
import static com.ringoid.PersonProperties.SETTINGS_TIMEZONE;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;

public class WhoReadyForPush {
    private final static Log log = LoggerFactory.getLogger(WhoReadyForPush.class);

    private final static String WHO_READY_QUERY =
            String.format(
                    "MATCH (n:%s) WHERE (n.%s < timestamp() - $maxPeriod OR NOT exists(n.%s)) " +
                            "AND (n.%s < timestamp()- $offlinePeriod) AND n.%s = TRUE " +
                            "RETURN DISTINCT n.%s AS userId ORDER BY userId SKIP $skipParam LIMIT $limitParam",
                    PERSON.getLabelName(), PUSH_WAS_SENT_AT.getPropertyName(), PUSH_WAS_SENT_AT.getPropertyName(),
                    LAST_ONLINE_TIME.getPropertyName(), SETTINGS_PUSH.getPropertyName(),
                    USER_ID.getPropertyName()
            );

    private static final String NEW_NOT_SEEN_QUERY = String.format(
            "MATCH (n:%s {%s:$sex})-[:%s]->(ph:%s) " +//1
                    "WHERE n.%s > $lastOnline " +//2
                    "WITH DISTINCT n " +//3
                    "RETURN count(n) as result",//4
            PERSON.getLabelName(), SEX.getPropertyName(), Relationships.UPLOAD_PHOTO.name(), Labels.PHOTO.getLabelName(),//1
            CREATED.getPropertyName()
    );

    public static PushResponse whoAreReady(PushRequest request, GraphDatabaseService database) {
        PushResponse response = new PushResponse();
        try (Transaction tx = database.beginTx()) {
            List<String> userIdsWithEnabledPushAndOffline = readyByTime(
                    request.getSkip(), request.getLimit(), request.getMaxPeriod(), request.getOfflinePeriod(),
                    database);
            response.setResultCount(userIdsWithEnabledPushAndOffline.size());

            List<Node> usersInGoodTimezone = filterByTimeZone(
                    userIdsWithEnabledPushAndOffline, request.getMinH(), request.getMaxH(), database);
            List<PushObject> objs = enrichWithLikeMatchAndMessage(usersInGoodTimezone);
            objs = enrichNewPersonCountIfNeeded(objs, database);
            List<PushObject> finalResult = new ArrayList<>();
            for (PushObject each : objs) {
                if (each.getNewLikeCount() != 0 ||
                        each.getNewMatchCount() != 0 ||
                        each.getNewMessageCount() != 0) {
                    finalResult.add(each);
                } else if (Objects.equals("male", each.getSex()) && each.getNewProfiles() >= request.getMinForMen()) {
                    finalResult.add(each);
                } else if (Objects.equals("female", each.getSex()) && each.getNewProfiles() >= request.getMinForWomen()) {
                    finalResult.add(each);
                }
            }
            response.setUsers(finalResult);
            tx.success();
        }
        return response;
    }

    private static List<String> readyByTime(int skip, int limit, int maxPeriod, int offlinePeriod, GraphDatabaseService database) {
        List<String> finalResult = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("maxPeriod", maxPeriod);
        params.put("offlinePeriod", offlinePeriod);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result result = database.execute(WHO_READY_QUERY, params);
        while (result.hasNext()) {
            Map<String, Object> mapResult = result.next();
            String userId = (String) mapResult.get("userId");
            finalResult.add(userId);
        }
        log.info("readyByTime query : %s", WHO_READY_QUERY);
        log.info("readyByTime result : %s", finalResult.size());
        return finalResult;
    }

    private static List<Node> filterByTimeZone(List<String> sourceIds, int minH, int maxH, GraphDatabaseService database) {
        List<Node> finalResult = new ArrayList<>();
        for (String eachId : sourceIds) {
            Node node = database.findNode(Label.label(Labels.PERSON.getLabelName()), USER_ID.getPropertyName(), eachId);
            if (Objects.isNull(node) || node.hasLabel(Label.label(Labels.HIDDEN.getLabelName()))) {
                log.info("filterByTimeZone : remove %s coz null or hidden", eachId);
                continue;
            }
            long timeZone = (Long) node.getProperty(SETTINGS_TIMEZONE.getPropertyName(), 100L);
            if (timeZone == 100L) {
                log.info("filterByTimeZone : remove %s coz timeZone is null", eachId);
                continue;
            }
            OffsetTime time = OffsetTime.now(ZoneOffset.UTC);
            time = time.plusHours(timeZone);
            int currentUserHour = time.getHour();
            if (currentUserHour < minH || currentUserHour >= maxH) {
                log.info("filterByTimeZone : remove %s coz currentUserHour is %s, minH %s and maxH %s",
                        eachId, currentUserHour, minH, maxH);
                continue;
            }
            finalResult.add(node);
        }
        log.info("filterByTimeZone : result size %s", finalResult.size());
        return finalResult;
    }

    private static List<PushObject> enrichWithLikeMatchAndMessage(List<Node> source) {
        List<PushObject> finalResult = new ArrayList<>();
        for (Node eachNode : source) {
            long lastOnlineTime = (Long) eachNode.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
            if (lastOnlineTime == 0) {
                continue;
            }
            String sex = (String) eachNode.getProperty(SEX.getPropertyName(), "na");
            if (Objects.equals("na", sex)) {
                continue;
            }
            String locale = (String) eachNode.getProperty(SETTINGS_LOCALE.getPropertyName(), "na");
            if (Objects.equals("na", locale)) {
                continue;
            }
            String userId = (String) eachNode.getProperty(USER_ID.getPropertyName(), "na");
            if (Objects.equals("na", userId)) {
                continue;
            }
            PushObject obj = new PushObject();
            obj.setUserId(userId);
            obj.setLastOnlineTime(lastOnlineTime);
            obj.setSex(sex);
            obj.setLocale(locale);

            Iterable<Relationship> allRels = eachNode.getRelationships(
                    RelationshipType.withName(Relationships.LIKE.name()),
                    RelationshipType.withName(Relationships.MATCH.name()),
                    RelationshipType.withName(Relationships.MESSAGE.name())
            );

            for (Relationship eachRel : allRels) {
                if (eachRel.isType(RelationshipType.withName(Relationships.LIKE.name())) &&
                        eachRel.getStartNode().getId() != eachNode.getId()) {
                    long likeAt = (Long) eachRel.getProperty(LikeProperties.LIKE_AT.getPropertyName(), 0L);
                    if (likeAt > lastOnlineTime) {
                        obj.setNewLikeCount(obj.getNewLikeCount() + 1L);
                    }
                } else if (eachRel.isType(RelationshipType.withName(Relationships.MATCH.name()))) {
                    long matchAt = (Long) eachRel.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                    if (matchAt > lastOnlineTime) {
                        obj.setNewMatchCount(obj.getNewMatchCount() + 1L);
                    }
                } else if (eachRel.isType(RelationshipType.withName(Relationships.MESSAGE.name()))) {
                    long msgAt = (Long) eachRel.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                    if (msgAt > lastOnlineTime) {
                        obj.setNewMessageCount(obj.getNewMessageCount() + 1L);
                    }
                }
            }
            finalResult.add(obj);
        }
        log.info("enrichWithLikeMatchAndMessage : result size %s", finalResult.size());
        log.info("enrichWithLikeMatchAndMessage : result : %s", finalResult);
        return finalResult;
    }

    private static List<PushObject> enrichNewPersonCountIfNeeded(List<PushObject> source, GraphDatabaseService database) {
        for (PushObject each : source) {
            log.info("enrichNewPersonCountIfNeeded : start enrich %s", each.getUserId());
            if (each.getNewLikeCount() != 0 ||
                    each.getNewMatchCount() != 0 ||
                    each.getNewMessageCount() != 0) {
                log.info("enrichNewPersonCountIfNeeded : %s has nL,nM,nMM so d't enrich", each.getUserId());
                continue;
            }
            String targetSex = "female";
            if (Objects.equals("female", each.getSex())) {
                targetSex = "male";
            }
            Map<String, Object> params = new HashMap<>();
            params.put("lastOnline", each.getLastOnlineTime());
            params.put("sex", targetSex);
            Result result = database.execute(NEW_NOT_SEEN_QUERY, params);
            long count = 0L;
            while (result.hasNext()) {
                Map<String, Object> mapResult = result.next();
                count = (Long) mapResult.get("result");
            }
            log.info("enrichNewPersonCountIfNeeded : params %s", params);
            log.info("enrichNewPersonCountIfNeeded : request %s", NEW_NOT_SEEN_QUERY);
            log.info("enrichNewPersonCountIfNeeded : result %s", count);
            each.setNewProfiles(count);
        }
        log.info("enrichNewPersonCountIfNeeded : return final result %s", source);
        return source;
    }

}
