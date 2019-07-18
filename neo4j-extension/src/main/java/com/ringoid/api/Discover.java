package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.LOCATION;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PersonProperties.YEAR;
import static com.ringoid.api.Utils.discoverSortProfilesSeenPart;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortDiscoverUnseenPartProfiles;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortNewFacesUnseenPhotos;

public class Discover {

    private final static Log log = LoggerFactory.getLogger(Discover.class);

    private final static int HARDCODED_MAX_FEED_NUM = 100;

    //$sourceUserId, $targetSex, $limitParam
    private static final String GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_DESC = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId}) WITH sourceUser " +//1
                    "MATCH (target:%s) WHERE target.%s = $targetSex" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "WITH sourceUser, target " +
                    "WHERE NOT (target)<-[:%s|%s|%s|%s|%s|%s|%s|%s]-(sourceUser) " +//2.1
                    "AND (target)-[:%s]->(:%s) AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS onlineTime ORDER BY onlineTime DESC LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(),//1
            PERSON.getLabelName(), SEX.getPropertyName(),//2
            Relationships.VIEW, Relationships.VIEW_IN_LIKES_YOU, Relationships.VIEW_IN_MATCHES, Relationships.VIEW_IN_MESSAGES, Relationships.BLOCK, Relationships.LIKE, Relationships.MESSAGE, Relationships.MATCH,//2.1
            Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(), Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    //$sourceUserId, $targetSex, $skipParam, $limitParam
    private static final String GEO_SEEN_SORTED_BY_ONLINE_TIME_DESC = String.format(
            "MATCH (sourceUser:%s {%s:$sourceUserId})-[:%s]->(target:%s)-[:%s]->(:%s) " +//1
                    "WHERE target.%s = $targetSex" +//2
                    //!!!dynamic part
                    "AGE_FILTER DISTANCE_FILTER" +
                    //!!!
                    "AND NOT('%s' in labels(target)) " +//2.2
                    "RETURN DISTINCT target.%s AS userId, target.%s AS onlineTime ORDER BY onlineTime DESC SKIP $skipParam LIMIT $limitParam",//3
            PERSON.getLabelName(), USER_ID.getPropertyName(), Relationships.VIEW.name(), PERSON.getLabelName(), Relationships.UPLOAD_PHOTO.name(), PHOTO.getLabelName(),//1
            SEX.getPropertyName(),//2
            Labels.HIDDEN.getLabelName(),//2.2
            USER_ID.getPropertyName(), LAST_ONLINE_TIME.getPropertyName()//3
    );

    private static String constructFilteredQuery(String baseQuery, Filter filter) {
        if (Objects.isNull(filter)) {
            return baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
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
            if (Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                return baseQuery.replaceFirst("AGE_FILTER DISTANCE_FILTER", " ");
            } else if (!Objects.equals("", agePart) && Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst(" DISTANCE_FILTER", "");
                return tmpStr.replaceFirst("AGE_FILTER", agePart + " ");
            } else if (Objects.equals("", agePart) && !Objects.equals("", distancePart)) {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER ", "");
                return tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            } else {
                String tmpStr = baseQuery.replaceFirst("AGE_FILTER", agePart);
                return tmpStr.replaceFirst("DISTANCE_FILTER", distancePart + " ");
            }
        }
    }

    private static List<Node> execute(String query, String sourceUserId, String targetSex, int skip, int limit,
                                      GraphDatabaseService database, MetricRegistry metrics) {
        List<Node> result = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        params.put("sourceUserId", sourceUserId);
        params.put("targetSex", targetSex);
        params.put("skipParam", skip);
        params.put("limitParam", limit);
        Result queryResult = database.execute(query, params, 10L, TimeUnit.SECONDS);
        while (queryResult.hasNext()) {
            Node node = database.findNode(Label.label(Labels.PERSON.getLabelName()), USER_ID.getPropertyName(), queryResult.next().get("userId"));
            if (Objects.nonNull(node)) {
                result.add(node);
            }
        }
        return result;
    }

    public static NewFacesResponse discover(DiscoverRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        log.info("handle discover request %s", request);
        NewFacesResponse response = new NewFacesResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request discover for user that not exist, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getLastActionTime() <= actionTime) {

                String sex = (String) sourceUser.getProperty(SEX.getPropertyName(), "male");
                String targetSex = "female";
                if (Objects.equals("female", sex)) {
                    targetSex = "male";
                }

                List<Node> unseen = unseenFilteredResult(request, targetSex, database, metrics);
                Map<String, List<Node>> groups = sortByOnlineGroups(unseen);
                List<Node> onlineUnseen = groups.get("ONLINE");
                onlineUnseen = sortDiscoverUnseenPartProfiles(onlineUnseen);
                response.getNewFaces().addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), onlineUnseen, true, sourceUser, database, metrics));

                int resultSize = request.getLimit();
                if (response.getNewFaces().isEmpty()) {
                    resultSize = HARDCODED_MAX_FEED_NUM;
                }

                Map<String, List<Node>> seenGroups = new HashMap<>();

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> seen = seenFilteredResult(request, targetSex, resultSize, database, metrics);
                    seenGroups = sortByOnlineGroups(seen);
                    List<Node> onlineSeen = seenGroups.get("ONLINE");
                    onlineSeen = discoverSortProfilesSeenPart(sourceUser, onlineSeen);
                    response.getNewFaces().addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), onlineSeen, false, sourceUser, database, metrics));

                }

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> oldUnseen = groups.get("OLD");
                    oldUnseen = sortDiscoverUnseenPartProfiles(oldUnseen);
                    response.getNewFaces().addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), oldUnseen, true, sourceUser, database, metrics));
                }

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> oldSeen = seenGroups.get("OLD");
                    oldSeen = discoverSortProfilesSeenPart(sourceUser, oldSeen);
                    response.getNewFaces().addAll(createProfileListWithResizedAndSortedPhotos(request.getResolution(), oldSeen, false, sourceUser, database, metrics));
                }

                if (response.getNewFaces().size() > resultSize) {
                    response.setNewFaces(response.getNewFaces().subList(0, resultSize));
                }

            }
            tx.success();
        }
        return response;
    }


    private static List<Node> unseenFilteredResult(DiscoverRequest request, String targetSex, GraphDatabaseService database, MetricRegistry metrics) {
        String query = constructFilteredQuery(GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_DESC, request.getFilter());
        List<Node> result = execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static List<Node> seenFilteredResult(DiscoverRequest request, String targetSex, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = constructFilteredQuery(GEO_SEEN_SORTED_BY_ONLINE_TIME_DESC, request.getFilter());
        List<Node> result = execute(query, request.getUserId(), targetSex, 0, limit, database, metrics);
        result = filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static Map<String, List<Node>> sortByOnlineGroups(List<Node> source) {
        Map<String, List<Node>> result = new HashMap<>();
        result.put("ONLINE", new ArrayList<Node>());
        result.put("OLD", new ArrayList<Node>());
        for (Node each : source) {
            long onlineTime = (Long) each.getProperty(LAST_ONLINE_TIME.getPropertyName(), 0L);
            long now = System.currentTimeMillis();
            if (now - onlineTime < 1_000 * 60 * 60 * 24 * 3) {
                result.get("ONLINE").add(each);
            } else {
                result.get("OLD").add(each);
            }
        }
        return result;
    }

    private static List<Node> filterNodesByVisiblePhotos(String sourceUserId, List<Node> source) {
        Iterator<Node> it = source.iterator();
        while (it.hasNext()) {
            Node each = it.next();
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

    private static List<Profile> createProfileListWithResizedAndSortedPhotos(String resolution, List<Node> source,
                                                                             boolean isItUnseenPart, Node sourceUser, GraphDatabaseService database,
                                                                             MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        List<Profile> profileList = new ArrayList<>(source.size());
        for (Node eachProfile : source) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
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
        metrics.histogram("discover_createProfileListWithResizedAndSortedPhotos").update(fullTime);
        return profileList;
    }

}
