package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.MatchProperties;
import com.ringoid.MessageRelationshipProperties;
import com.ringoid.PersonProperties;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.spatial.Point;
import org.neo4j.logging.Log;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.CHILDREN;
import static com.ringoid.PersonProperties.COMPANY;
import static com.ringoid.PersonProperties.EDUCATION_TEXT;
import static com.ringoid.PersonProperties.EDU_LEVEL;
import static com.ringoid.PersonProperties.INCOME;
import static com.ringoid.PersonProperties.JOB_TITLE;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.LOCATION;
import static com.ringoid.PersonProperties.NAME;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.STATUS_TEXT;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.WHERE_I_LIVE;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.PhotoProperties.ONLY_OWNER_CAN_SEE;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortNewFacesUnseenPhotos;

public class QueryUtils {

    private static final Log log = LoggerFactory.getLogger(QueryUtils.class);

    static final String LOCATION_EXISTS = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) return exists(sourceUser.%s) as lexists",
            PERSON.getLabelName(), USER_ID.getPropertyName(), LOCATION.getPropertyName()
    );

    //$sourceUserId, $targetSex, $limitParam, onlineTime
    static final String DISCOVER_ONLINE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) WHERE target.%s = $targetSex AND target.%s >= $onlineTime AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "WITH sourceUser, target " +
                    "WHERE (NOT (target)<-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1.2
                    "AND (target)-[:%s]->(:%s) AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, distance(sourceUser.%s, target.%s) as dist ORDER BY dist LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, //2.1
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1.2
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LOCATION.getPropertyName(), LOCATION.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $limitParam, onlineTime, activeTime
    static final String DISCOVER_ACTIVE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) WHERE target.%s = $targetSex AND target.%s >= $activeTime AND target.%s < $onlineTime AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "WITH sourceUser, target " +
                    "WHERE (NOT (target)<-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1
                    "AND (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//2.1.2
                    "AND (target)-[:%s]->(:%s) AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, distance(sourceUser.%s, target.%s) as dist ORDER BY dist LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, //2.1
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1.2
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LOCATION.getPropertyName(), LOCATION.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam, $onlineTime
    static final String DISCOVER_ONLINE_USERS_GEO_SEEN_SORTED_BY_DISTANCE = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]->(target:%s)-[:%s]->(:%s) " +//1
                    "WHERE (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//1.2
                    "AND target.%s = $targetSex AND target.%s >= $onlineTime AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, distance(sourceUser.%s, target.%s) as dist ORDER BY dist SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.VIEW.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//1.2
            SEX.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LOCATION.getPropertyName(), LOCATION.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam, $onlineTime
    static final String DISCOVER_ACTIVE_USERS_GEO_SEEN_SORTED_BY_DISTANCE = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]->(target:%s)-[:%s]->(:%s) " +//1
                    "WHERE (NOT (target)-[:%s|%s|%s|%s]-(sourceUser)) " +//1.2
                    "AND target.%s = $targetSex AND target.%s >= $activeTime AND target.%s < $onlineTime AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, distance(sourceUser.%s, target.%s) as dist ORDER BY dist SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.VIEW.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//1.2
            SEX.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(), LAST_ONLINE_TIME.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LOCATION.getPropertyName(), LOCATION.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    static final String GET_LC_LIKES_GEO_UNSEEN_SORTED_BY_USER_ID = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false) AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT (sourceUser)-[:%s]->(target) " +//2.1
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId ORDER BY userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Relationships.VIEW_IN_LIKES_YOU.name(),//2.1
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    static final String GET_LC_LIKES_GEO_SEEN_SORTED_BY_USER_ID = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false) AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND (sourceUser)-[:%s]->(target) " +//2.1
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId ORDER BY userId SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Relationships.VIEW_IN_LIKES_YOU.name(),//2.1
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_LIKES_NUM = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})<-[:%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false) AND exists(target.location)" +//2
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN count(DISTINCT target.%s) as num",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.LIKE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_MESSAGES_AND_MATCHES_GEO = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s|%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false) AND exists(target.location)" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.MESSAGE.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    //$sourceUserId
    static final String GET_LC_MESSAGES_NUM = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s|%s]-(target:%s)-[:%s]->(photo:%s) " +//1
                    "WHERE (NOT exists(photo.%s) OR photo.%s = false) AND exists(target.location)" +//2
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN count(DISTINCT target.%s) as num",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.MATCH.name(), Relationships.MESSAGE, PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName()//3
    );

    public static Map<String, List<DistanceWrapper>> seen(Node sourceNode, Filter filter, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        Map<String, List<DistanceWrapper>> result = new HashMap<>();
        result.put("online", new ArrayList<DistanceWrapper>());
        result.put("active", new ArrayList<DistanceWrapper>());

        Set<Long> blocks = new HashSet<>();
        Set<Long> matches = new HashSet<>();
        Set<Long> messages = new HashSet<>();
        Set<Long> likes = new HashSet<>();

        Iterable<Relationship> iterable = sourceNode.getRelationships(
                RelationshipType.withName(Relationships.BLOCK.name()),
                RelationshipType.withName(Relationships.LIKE.name()),
                RelationshipType.withName(Relationships.MESSAGE.name()),
                RelationshipType.withName(Relationships.MATCH.name()));


        for (Relationship each : iterable) {
            Node other = each.getOtherNode(sourceNode);
            if (Objects.nonNull(other) && other.hasLabel(Label.label(PERSON.getLabelName()))) {
                if (each.isType(RelationshipType.withName(Relationships.BLOCK.name()))) {
                    blocks.add(other.getId());
                } else if (each.isType(RelationshipType.withName(Relationships.LIKE.name()))) {
                    likes.add(other.getId());
                } else if (each.isType(RelationshipType.withName(Relationships.MESSAGE.name()))) {
                    messages.add(other.getId());
                } else if (each.isType(RelationshipType.withName(Relationships.MATCH.name()))) {
                    matches.add(other.getId());
                }
            }
        }

        int counter = 0;
        iterable = sourceNode.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.OUTGOING);
        for (Relationship each : iterable) {
            Node other = each.getOtherNode(sourceNode);
            if (Objects.nonNull(other) && other.hasLabel(Label.label(PERSON.getLabelName()))) {
                if (!blocks.contains(other.getId()) && !likes.contains(other.getId()) &&
                        !messages.contains(other.getId()) && !matches.contains(other.getId())) {
                    boolean wasValid = putIfValid(sourceNode, filter, other, result);
                    if (wasValid) {
                        counter++;
                    }
                }
            }

            if (counter >= limit) {
                return result;
            }
        }

        return result;
    }

    private static boolean putIfValid(Node sourceNode, Filter filter, Node node, Map<String, List<DistanceWrapper>> result) {
        long dist = distance(sourceNode, node);
        if (dist < 0) {
            return false;
        }

        if (Objects.nonNull(filter)) {
            int age = ((Integer) node.getProperty(PersonProperties.YEAR.getPropertyName(), 3000L)).intValue();
            if (Objects.nonNull(filter.getMinAge())) {
                int minAge = LocalDate.now().getYear() - filter.getMinAge();
                if (age > minAge) {
                    return false;
                }
            }
            if (Objects.nonNull(filter.getMaxAge())) {
                int maxAge = LocalDate.now().getYear() - filter.getMaxAge();
                if (age < maxAge) {
                    return false;
                }
            }

            if (Objects.nonNull(filter.getMaxDistance())) {
                if (dist > filter.getMaxDistance()) {
                    return false;
                }
            }
        }

        DistanceWrapper wr = new DistanceWrapper();
        wr.distance = dist;
        wr.node = node;

        long time = (Long) node.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
        long onlineTime = System.currentTimeMillis() - 86_400_000L;//24h ago
        long activeTime = System.currentTimeMillis() - 3 * 86_400_000L;//3d ago

        boolean wasAdded = false;
        if (time >= onlineTime) {
            List<DistanceWrapper> list = result.get("online");
            list.add(wr);
            wasAdded = true;
        } else if (time < onlineTime && time >= activeTime) {
            List<DistanceWrapper> list = result.get("active");
            list.add(wr);
            wasAdded = true;
        }

        return wasAdded;
    }

    private static long distance(Node sourceNode, Node targetNode) {
        double R = 6372.8;
        Object obj = targetNode.getProperty(LOCATION.getPropertyName(), null);
        if (Objects.isNull(obj)) {
            return -1;
        }
        Point p2 = (Point) obj;
        if (Objects.isNull(p2.getCoordinate()) ||
                p2.getCoordinate().getCoordinate().isEmpty()) {
            return -1;
        }
        List<Double> coordinates = p2.getCoordinate().getCoordinate();
        Double lon2 = coordinates.get(0);
        Double lat2 = coordinates.get(1);

        obj = sourceNode.getProperty(LOCATION.getPropertyName(), null);
        //default Kiev location
        Double lon1 = 30.523550;
        Double lat1 = 50.450441;
        if (Objects.nonNull(obj)) {
            Point p1 = (Point) obj;
            if (Objects.nonNull(p1.getCoordinate()) &&
                    !p1.getCoordinate().getCoordinate().isEmpty()) {
                coordinates = p1.getCoordinate().getCoordinate();
                lon1 = coordinates.get(0);
                lat1 = coordinates.get(1);
            } else {
                log.info("warning_kiev, use Kiev location for userId [%s]", sourceNode.getProperty("user_id"));
            }
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        long result = Double.valueOf(R * c * 1000).longValue();
//        log.info("distance between source [%s] and target [%s] = %s", sourceNode.getProperty("user_id"), targetNode.getProperty("user_id"), result);
        return result;
    }

    public static String constructFilteredQuery(String baseQuery, Filter filter, boolean useKievLocation) {
//        log.info("constructFilteredQuery with useKievLocation : %s", Boolean.toString(useKievLocation));
        if (Objects.isNull(filter)) {
            String finalResult = baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
            if (useKievLocation) {
//                log.info("constructFilteredQuery query before : %s", finalResult);
                finalResult = finalResult.replaceAll(
                        String.format("distance\\(sourceUser\\.%s, target\\.%s\\)",
                                LOCATION.getPropertyName(), LOCATION.getPropertyName()),

                        String.format("distance(point({longitude: 30.523550, latitude: 50.450441}), target.%s)",
                                LOCATION.getPropertyName())
                );
//                log.info("constructFilteredQuery query after : %s", finalResult);
            }
            return finalResult;
        } else {
            String agePart = "";
            if (Objects.nonNull(filter.getMinAge())) {
                int minAge = LocalDate.now().getYear() - filter.getMinAge();
                agePart += String.format(" AND target.%s <= %s", YEAR.getPropertyName(), Integer.toString(minAge));
            }
            if (Objects.nonNull(filter.getMaxAge())) {
                int maxAge = LocalDate.now().getYear() - filter.getMaxAge();
                agePart += String.format(" AND target.%s >= %s", YEAR.getPropertyName(), Integer.toString(maxAge));
            }

            String distancePart = "";
            if (Objects.nonNull(filter.getMaxDistance())) {
                distancePart += String.format(" AND distance(sourceUser.%s, target.%s) <= %s",
                        LOCATION.getPropertyName(), LOCATION.getPropertyName(), filter.getMaxDistance().toString());
            }

            //return final result
            String finalResult;
            if (Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                finalResult = baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
            } else if (!Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst(" DISTANCE_FILTER", "");
                finalResult = tmpStr.replaceFirst("AGE_FILTER", agePart + " ");
            } else if (Objects.equals("", agePart) && !Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER ", "");
                finalResult = tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            } else {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER", agePart);
                finalResult = tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            }
            if (useKievLocation) {
//                log.info("constructFilteredQuery query before : %s", finalResult);
                finalResult = finalResult.replaceAll(
                        String.format("distance\\(sourceUser\\.%s, target\\.%s\\)",
                                LOCATION.getPropertyName(), LOCATION.getPropertyName()),

                        String.format("distance(point({longitude: 30.523550, latitude: 50.450441}), target.%s)",
                                LOCATION.getPropertyName())
                );
//                log.info("constructFilteredQuery query after : %s", finalResult);
            }
            return finalResult;
        }
    }

    public static int count(String query, String sourceUserId, GraphDatabaseService database, MetricRegistry metrics) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        Result queryResult = database.execute(query, params, 10L, TimeUnit.SECONDS);
        Long result = 0L;
        while (queryResult.hasNext()) {
            result = (Long) queryResult.next().get("num");
        }
        return result.intValue();
    }

    public static boolean locationExists(String sourceUserId, GraphDatabaseService database, MetricRegistry metrics) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
//        log.info("execute query : %s", LOCATION_EXISTS);
//        log.info("with params : sourceUserId : %s", sourceUserId);
        Result queryResult = database.execute(LOCATION_EXISTS, params, 10L, TimeUnit.SECONDS);
        while (queryResult.hasNext()) {
            Map<String, Object> resultMap = queryResult.next();
            boolean locationExist = (Boolean) resultMap.getOrDefault("lexists", false);
            return locationExist;
        }
        return false;
    }

    public static List<DistanceWrapper> execute(String query, String sourceUserId, String targetSex, int skip, int limit,
                                                GraphDatabaseService database, MetricRegistry metrics) {
        long onlineTime = System.currentTimeMillis() - 86_400_000L;//24h ago
        long activeTime = System.currentTimeMillis() - 3 * 86_400_000L;//3d ago
//        log.info("execute query for userId [%s] : %s\nwith params : sourceUserId : %s, targetSex : %s, skipParam : %s, limitParam : %s, onlineTime : %s, activeTime : %s", sourceUserId, query, sourceUserId, targetSex, Integer.toString(skip), Integer.toString(limit), Long.toString(onlineTime), Long.toString(activeTime));
        List<DistanceWrapper> result = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("targetSex", targetSex);
        params.put("onlineTime", onlineTime);
        params.put("activeTime", activeTime);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result queryResult = database.execute(query, params, 10L, TimeUnit.SECONDS);

        while (queryResult.hasNext()) {
            Map<String, Object> resultMap = queryResult.next();
            Node node = database.findNode(Label.label(Labels.PERSON.getLabelName()), USER_ID.getPropertyName(), resultMap.get("userId"));

            double distTmp = (Double) resultMap.getOrDefault("dist", -1.0);
            if (Objects.nonNull(node)) {
                DistanceWrapper wr = new DistanceWrapper();
                wr.node = node;
                wr.distance = Double.valueOf(distTmp).longValue();
                result.add(wr);
            }
        }
        return result;
    }

    public static List<DistanceWrapper> filterNodesByVisiblePhotos(String sourceUserId, List<DistanceWrapper> source) {
        Iterator<DistanceWrapper> it = source.iterator();
        while (it.hasNext()) {
            Node each = it.next().node;
            String userId = (String) each.getProperty(USER_ID.getPropertyName(), "n/a");
            if (Objects.equals(sourceUserId, userId)) {
                it.remove();
            } else {
                boolean dontHaveVisiblePhotos = true;
                Iterable<Relationship> uploaded =
                        each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
                for (Relationship upls : uploaded) {
                    Node photo = upls.getOtherNode(each);

                    if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                        if (!onlyOwnerCanSee) {
                            dontHaveVisiblePhotos = false;
                            break;
                        }
                    }
                }
                if (dontHaveVisiblePhotos) {
                    it.remove();
                }
            }
        }
        return source;
    }

    public static List<Profile> createProfileListWithResizedAndSortedPhotos(String resolution, List<Node> source,
                                                                            boolean isItUnseenPart, Node sourceUser, GraphDatabaseService database,
                                                                            MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        List<Profile> profileList = new ArrayList<>(source.size());
        for (Node eachProfile : source) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
            prof.setUnseen(isItUnseenPart);
            if (isItUnseenPart) {
                prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortNewFacesUnseenPhotos(eachProfile), resolution, database));
            } else {
                prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), resolution, database));
            }
            if (!prof.getPhotos().isEmpty()) {
                prof = enrichProfile(eachProfile, sourceUser, prof);
                profileList.add(prof);
            }
        }
        long fullTime = System.currentTimeMillis() - start;
        metrics.histogram("discover/lc_createProfileListWithResizedAndSortedPhotos").update(fullTime);
        return profileList;
    }

    public static List<DistanceWrapper> sortDiscoverUnseenPartProfiles(List<DistanceWrapper> tmpResult) {
        if (tmpResult.isEmpty()) {
            return tmpResult;
        }

        Collections.sort(tmpResult, new Comparator<DistanceWrapper>() {
            @Override
            public int compare(DistanceWrapper node1, DistanceWrapper node2) {
                //now count photos
                int photoCounter1 = 0;
                for (Relationship each : node1.node.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node1.node);
                    if (Objects.nonNull(eachPhoto) && eachPhoto.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        if (!((Boolean) eachPhoto.getProperty(ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                            photoCounter1++;
                        }
                    }
                }
                int photoCounter2 = 0;
                for (Relationship each : node2.node.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node2.node);
                    if (Objects.nonNull(eachPhoto) && eachPhoto.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                        if (!((Boolean) eachPhoto.getProperty(ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                            photoCounter2++;
                        }
                    }
                }

                if (photoCounter1 > photoCounter2) {
                    return -1;
                } else if (photoCounter1 < photoCounter2) {
                    return 1;
                }

                long likeCounter1 = (Long) node1.node.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                long likeCounter2 = (Long) node2.node.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                if (likeCounter1 > likeCounter2) {
                    return -1;
                } else if (likeCounter1 < likeCounter2) {
                    return 1;
                }

                return 0;
            }
        });
        return tmpResult;
    }

    public static List<DistanceWrapper> discoverSortProfilesSeenPart(Node sourceUser, List<DistanceWrapper> sourceList) {
        List<Utils.NodeWrapper> wrapperList = new ArrayList<>(sourceList.size());
        for (DistanceWrapper each : sourceList) {
            int photoCounter = 0;
            int likeCounter = 0;
            int viewedFromSource = 0;
            Iterable<Relationship> uploads = each.node.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship eachUpload : uploads) {
                Node photo = eachUpload.getOtherNode(each.node);
                if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                    Boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                    if (!onlyOwnerCanSee) {
                        photoCounter++;
                        Iterable<Relationship> photoLikes = photo.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
                        //todo:here we can check who liked this photo - hidden user or not, but mb later
                        for (Relationship eachLike : photoLikes) {
                            likeCounter++;
                        }
                        Iterable<Relationship> viewRels = photo.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.INCOMING);
                        for (Relationship eachView : viewRels) {
                            Node other = eachView.getOtherNode(photo);
                            if (other.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                                    other.getId() == sourceUser.getId()) {
                                viewedFromSource++;
                                break;
                            }
                        }
                    }
                }//end each single photo
            }//end loop by all photos
            Utils.NodeWrapper nodeWrapper = new Utils.NodeWrapper();
            nodeWrapper.node = each.node;
            nodeWrapper.allPhotoCount = photoCounter;
            nodeWrapper.likes = likeCounter;
            nodeWrapper.unseenPhotos = photoCounter - viewedFromSource;
            nodeWrapper.distanceWrapper = each;
            wrapperList.add(nodeWrapper);
        }//end
        Collections.sort(wrapperList, new Comparator<Utils.NodeWrapper>() {
            @Override
            public int compare(Utils.NodeWrapper node1, Utils.NodeWrapper node2) {
                if (node1.unseenPhotos > node2.unseenPhotos) {
                    return -1;
                } else if (node1.unseenPhotos < node2.unseenPhotos) {
                    return 1;
                }

                if (node1.allPhotoCount > node2.allPhotoCount) {
                    return -1;
                } else if (node1.allPhotoCount < node2.allPhotoCount) {
                    return 1;
                }

                if (node1.likes > node2.likes) {
                    return -1;
                } else if (node1.likes < node2.likes) {
                    return 1;
                }
                return 0;
            }
        });
        List<DistanceWrapper> result = new ArrayList<>(wrapperList.size());
        for (Utils.NodeWrapper eachWrapper : wrapperList) {
            result.add(eachWrapper.distanceWrapper);
        }
        return result;
    }

    public static List<Node> sortLCUnseenPartProfilesForWomen(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                return countSortingScores(node1, null).compareTo(countSortingScores(node2, null));
            }
        });

        return tmpResult;
    }

    public static Integer countSortingScores(Node node, Profile profile) {
        int scores = 0;
        int totalChatCount = Utils.countConversationWithMessagesMoreThan(node, 5);
        scores += 4 * totalChatCount;

        if (Objects.nonNull(profile)) {
            profile.setTotalChatCount(totalChatCount);
            profile.setTotalChatCountScores(4 * totalChatCount);
        }

        int totalMatchesCount = Utils.countMatchesAndChats(node);
        scores += totalMatchesCount;

        if (Objects.nonNull(profile)) {
            profile.setTotalMatchesCount(totalMatchesCount);
            profile.setTotalMatchesCountScores(totalMatchesCount);
        }

        int photos = Utils.countPhotos(node);
        if (photos > 0) {
            Double tmp = 10 * Math.log10(new Double(photos));
            scores += tmp.intValue();
            if (Objects.nonNull(profile)) {
                profile.setPhotosCount(photos);
                profile.setPhotosCountScores(tmp.intValue());
            }
        }


        int income = (Integer) node.getProperty(INCOME.getPropertyName(), 0);
        if (income != 0) {
            scores += 2;
            if (Objects.nonNull(profile)) {
                profile.setIncomeScores(2);
            }
        }

        int children = (Integer) node.getProperty(CHILDREN.getPropertyName(), 0);
        if (children != 0) {
            scores += 3;
            if (Objects.nonNull(profile)) {
                profile.setChildrenScores(3);
            }
        }

        int eduLevel = (Integer) node.getProperty(EDU_LEVEL.getPropertyName(), 0);
        if (eduLevel != 0) {
            scores += 1;
            if (Objects.nonNull(profile)) {
                profile.setEduScores(1);
            }
        }

        String jobTitle = (String) node.getProperty(JOB_TITLE.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", jobTitle)) {
            scores += 2;
            if (Objects.nonNull(profile)) {
                profile.setJobTitleScore(2);
            }
        }

        String company = (String) node.getProperty(COMPANY.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", company)) {
            scores += 2;
            if (Objects.nonNull(profile)) {
                profile.setCompanyScores(2);
            }
        }

        String educationText = (String) node.getProperty(EDUCATION_TEXT.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", educationText)) {
            scores += 1;
            if (Objects.nonNull(profile)) {
                profile.setEduScores(profile.getEduScores() + 1);
            }
        }

        String statusText = (String) node.getProperty(STATUS_TEXT.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", statusText)) {
            scores += 1;
            if (Objects.nonNull(profile)) {
                profile.setStatusScores(1);
            }
        }

        String name = (String) node.getProperty(NAME.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", name)) {
            scores += 3;
            if (Objects.nonNull(profile)) {
                profile.setNameScores(3);
            }
        }

        String whereILive = (String) node.getProperty(WHERE_I_LIVE.getPropertyName(), "unknown");
        if (!Objects.equals("unknown", whereILive)) {
            scores += 1;
            if (Objects.nonNull(profile)) {
                profile.setCityScores(1);
            }
        }

        if (Objects.nonNull(profile)) {
            profile.setTotalScores(scores);
        }

        return Integer.valueOf(scores);
    }

    public static List<Node> sortLCUnseenPartProfilesForMen(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long likeCounter1 = (Long) node1.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                long likeCounter2 = (Long) node2.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
                if (likeCounter1 > likeCounter2) {
                    return -1;
                } else if (likeCounter1 < likeCounter2) {
                    return 1;
                }

                //now count photos
                int photoCounter1 = 0;
                for (Relationship each : node1.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node1);
                    if (Objects.nonNull(eachPhoto) &&
                            !((Boolean) eachPhoto.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                        photoCounter1++;
                    }
                }
                int photoCounter2 = 0;
                for (Relationship each : node2.getRelationships(Direction.OUTGOING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()))) {
                    Node eachPhoto = each.getOtherNode(node2);
                    if (Objects.nonNull(eachPhoto) &&
                            !((Boolean) eachPhoto.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false))) {
                        photoCounter2++;
                    }
                }

                if (photoCounter1 > photoCounter2) {
                    return -1;
                } else if (photoCounter1 < photoCounter2) {
                    return 1;
                }

                //compare last online time
                long lastOnline1 = (Long) node1.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
                long lastOnline2 = (Long) node2.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
                if (lastOnline1 > lastOnline2) {
                    return -1;
                } else if (lastOnline1 < lastOnline2) {
                    return 1;
                }
                return 0;
            }
        });
        return tmpResult;
    }

    public static List<Node> sortGetLCSeenPartProfiles(Node sourceUser, List<Node> sourceList) {
        List<Utils.NodeWrapper> wrapperList = new ArrayList<>(sourceList.size());
        for (Node each : sourceList) {
            long lastOnlineTime = (Long) each.getProperty(PersonProperties.LAST_ONLINE_TIME.getPropertyName(), 0L);
            int photoCounter = 0;
            int likeCounter = 0;
            int viewedFromSource = 0;
            Iterable<Relationship> uploads = each.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship eachUpload : uploads) {
                Node photo = eachUpload.getOtherNode(each);
                if (photo.hasLabel(Label.label(Labels.PHOTO.getLabelName()))) {
                    Boolean onlyOwnerCanSee = (Boolean) photo.getProperty(PhotoProperties.ONLY_OWNER_CAN_SEE.getPropertyName(), false);
                    if (!onlyOwnerCanSee) {
                        photoCounter++;
                        Iterable<Relationship> photoLikes = photo.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
                        //todo:here we can check who liked this photo - hidden user or not, but mb later
                        for (Relationship eachLike : photoLikes) {
                            likeCounter++;
                        }
                        Iterable<Relationship> viewRels = photo.getRelationships(RelationshipType.withName(Relationships.VIEW.name()), Direction.INCOMING);
                        for (Relationship eachView : viewRels) {
                            Node other = eachView.getOtherNode(photo);
                            if (other.hasLabel(Label.label(Labels.PERSON.getLabelName())) &&
                                    other.getId() == sourceUser.getId()) {
                                viewedFromSource++;
                                break;
                            }
                        }
                    }
                }//end each single photo
            }//end loop by all photos

            Utils.NodeWrapper nodeWrapper = new Utils.NodeWrapper();
            nodeWrapper.node = each;
            nodeWrapper.allPhotoCount = photoCounter;
            nodeWrapper.likes = likeCounter;
            nodeWrapper.unseenPhotos = photoCounter - viewedFromSource;
            nodeWrapper.onlineTime = lastOnlineTime;

            LocalDateTime timeOnline =
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(lastOnlineTime),
                            TimeZone.getDefault().toZoneId());
            LocalDateTime timeNow = LocalDateTime.now();
            int days = Long.valueOf(ChronoUnit.DAYS.between(timeOnline, timeNow)).intValue();
            if (days < 0) {
                days = days * (-1);
            }
            nodeWrapper.dayNum = days;

            wrapperList.add(nodeWrapper);
        }//end
        Collections.sort(wrapperList, new Comparator<Utils.NodeWrapper>() {
            @Override
            public int compare(Utils.NodeWrapper node1, Utils.NodeWrapper node2) {
                if (node1.dayNum > node2.dayNum) {
                    return 1;
                } else if (node1.dayNum < node2.dayNum) {
                    return -1;
                }

                if (node1.unseenPhotos > node2.unseenPhotos) {
                    return -1;
                } else if (node1.unseenPhotos < node2.unseenPhotos) {
                    return 1;
                }

                if (node1.allPhotoCount > node2.allPhotoCount) {
                    return -1;
                } else if (node1.allPhotoCount < node2.allPhotoCount) {
                    return 1;
                }

                if (node1.likes > node2.likes) {
                    return -1;
                } else if (node1.likes < node2.likes) {
                    return 1;
                }

                if (node1.onlineTime > node2.onlineTime) {
                    return -1;
                } else if (node1.onlineTime < node2.onlineTime) {
                    return 1;
                }
                return 0;
            }
        });
        List<Node> result = new ArrayList<>(wrapperList.size());
        for (Utils.NodeWrapper eachWrapper : wrapperList) {
            result.add(eachWrapper.node);
        }
        return result;
    }

    public static List<Node> sortProfilesByLastMessageAtOrMatchTime(Node sourceUser, List<Node> tmpResult) {
        Collections.sort(tmpResult, new Comparator<Node>() {
            @Override
            public int compare(Node node1, Node node2) {
                long lastMessageAt1 = 0;
                Iterable<Relationship> messages1 = node1.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages1) {
                    Node other = each.getOtherNode(node1);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt1 = (Long) each.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }
                long time1 = lastMessageAt1;
                if (time1 == 0L) {
                    Iterable<Relationship> matches1 = node1.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MATCH.name()));
                    for (Relationship each : matches1) {
                        Node other = each.getOtherNode(node1);
                        if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                            long matchTime1 = (Long) each.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                            time1 = matchTime1;
                            break;
                        }
                    }
                }

                long lastMessageAt2 = 0;
                Iterable<Relationship> messages2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MESSAGE.name()));
                for (Relationship each : messages2) {
                    Node other = each.getOtherNode(node2);
                    if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                        lastMessageAt2 = (Long) each.getProperty(MessageRelationshipProperties.MSG_AT.getPropertyName(), 0L);
                        break;
                    }
                }
                long time2 = lastMessageAt2;
                if (time2 == 0L) {
                    Iterable<Relationship> matches2 = node2.getRelationships(Direction.BOTH, RelationshipType.withName(Relationships.MATCH.name()));
                    for (Relationship each : matches2) {
                        Node other = each.getOtherNode(node2);
                        if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == sourceUser.getId()) {
                            long matchTime2 = (Long) each.getProperty(MatchProperties.MATCH_AT.getPropertyName(), 0L);
                            time2 = matchTime2;
                            break;
                        }
                    }
                }

                if (time1 > time2) {
                    return -1;
                } else if (time1 < time2) {
                    return 1;
                }

                return 0;
            }
        });
        return tmpResult;
    }

}
