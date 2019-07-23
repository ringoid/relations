package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.LAST_ONLINE_TIME;
import static com.ringoid.PersonProperties.SEX;
import static com.ringoid.PersonProperties.USER_ID;

public class Discover {

    private final static Log log = LoggerFactory.getLogger(Discover.class);

    private final static int HARDCODED_MAX_FEED_NUM = 100;

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
                onlineUnseen = QueryUtils.sortDiscoverUnseenPartProfiles(onlineUnseen);
                response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), onlineUnseen, true, sourceUser, database, metrics));

                int resultSize = request.getLimit();
                if (response.getNewFaces().isEmpty()) {
                    resultSize = HARDCODED_MAX_FEED_NUM;
                }

                Map<String, List<Node>> seenGroups = new HashMap<>();

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> seen = seenFilteredResult(request, targetSex, resultSize, database, metrics);
                    seenGroups = sortByOnlineGroups(seen);
                    List<Node> onlineSeen = seenGroups.get("ONLINE");
                    onlineSeen = QueryUtils.discoverSortProfilesSeenPart(sourceUser, onlineSeen);
                    response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), onlineSeen, false, sourceUser, database, metrics));

                }

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> oldUnseen = groups.get("OLD");
                    oldUnseen = QueryUtils.sortDiscoverUnseenPartProfiles(oldUnseen);
                    response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), oldUnseen, true, sourceUser, database, metrics));
                }

                if (response.getNewFaces().size() < resultSize) {
                    List<Node> oldSeen = seenGroups.get("OLD");
                    oldSeen = QueryUtils.discoverSortProfilesSeenPart(sourceUser, oldSeen);
                    response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), oldSeen, false, sourceUser, database, metrics));
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
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_GEO_NOT_SEEN_SORTED_BY_ONLINE_TIME_DESC, request.getFilter());
        List<Node> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static List<Node> seenFilteredResult(DiscoverRequest request, String targetSex, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_GEO_SEEN_SORTED_BY_ONLINE_TIME_DESC, request.getFilter());
        List<Node> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, limit, database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
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

}
