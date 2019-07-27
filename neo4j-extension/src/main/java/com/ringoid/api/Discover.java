package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
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

                List<Node> unseen = unseen(request, targetSex, database, metrics);
                response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), unseen, true, sourceUser, database, metrics));

                int resultSize = request.getLimit();
                if (response.getNewFaces().isEmpty()) {
                    resultSize = HARDCODED_MAX_FEED_NUM;
                }


                if (response.getNewFaces().size() < resultSize) {
                    List<Node> seen = seen(sourceUser, request, targetSex, database, metrics);
                    if (seen.size() > resultSize) {
                        seen = seen.subList(0, resultSize);
                    }
                    response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), seen, false, sourceUser, database, metrics));
                }

                if (response.getNewFaces().size() > resultSize) {
                    response.setNewFaces(response.getNewFaces().subList(0, resultSize));
                }

            }
            tx.success();
        }
        return response;
    }

    private static Map<Long, List<Node>> groupByDistance(List<DistanceWrapper> source, long step) {
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<Node>> map = new HashMap<>();
        for (DistanceWrapper each : source) {
            long eachDistance = each.distance;
            long tmp = eachDistance / step;
            if (tmp == 0L) {
                tmp = 1L;
            }
            if (eachDistance % step > 0) {
                tmp++;
            }
            List<Node> part = map.get(tmp);
            if (Objects.isNull(part)) {
                part = new ArrayList<>();
            }
            part.add(each.node);
            map.put(tmp, part);
        }
        return map;
    }

    private static void sortDiscoverUnseenGroups(Map<Long, List<Node>> groups) {
        for (Map.Entry<Long, List<Node>> each : groups.entrySet()) {
            QueryUtils.sortDiscoverUnseenPartProfiles(each.getValue());
        }
    }

    private static void sortDiscoverSeenGroups(Node source, Map<Long, List<Node>> groups) {
        for (Map.Entry<Long, List<Node>> each : groups.entrySet()) {
            QueryUtils.discoverSortProfilesSeenPart(source, each.getValue());
        }
    }

    private static List<Node> combineGroup(Map<Long, List<Node>> source) {
        List<Node> result = new ArrayList<>();
        List<Long> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        for (Long eachKey : keys) {
            result.addAll(source.get(eachKey));
        }
        return result;
    }

    private static List<Node> prepareNodesResult(List<Node> onlineGroup, List<Node> activeGroup,
                                                 int onlineNum, int activeNum) {
        List<Node> result = new ArrayList<>();
        while (true) {
            List<Node> tmpResult = new ArrayList<>(10);
            Iterator<Node> onlineIt = onlineGroup.iterator();
            Iterator<Node> activeIt = activeGroup.iterator();
            for (int i = 0; i < onlineNum; i++) {
                if (onlineIt.hasNext()) {
                    tmpResult.add(onlineIt.next());
                    onlineIt.remove();
                }
            }
            for (int i = 0; i < activeNum; i++) {
                if (activeIt.hasNext()) {
                    tmpResult.add(activeIt.next());
                    activeIt.remove();
                }
            }

            if (tmpResult.isEmpty()) {
                return result;
            }

            Collections.shuffle(tmpResult);
            result.addAll(tmpResult);
        }
    }

    private static List<Node> seen(Node sourceNode, DiscoverRequest request, String targetSex, GraphDatabaseService database, MetricRegistry metrics) {
        List<DistanceWrapper> onlineSeen = onlineSeenFilteredResult(request, targetSex, 1000, database, metrics);
        List<DistanceWrapper> activeSeen = activeSeenFilteredResult(request, targetSex, 1000, database, metrics);

        Map<Long, List<Node>> onlineSeenDistanceGroup = groupByDistance(onlineSeen, 15_000L);
        sortDiscoverSeenGroups(sourceNode, onlineSeenDistanceGroup);
        Map<Long, List<Node>> activeSeenDistanceGroup = groupByDistance(activeSeen, 15_000L);
        sortDiscoverSeenGroups(sourceNode, activeSeenDistanceGroup);

        List<Node> onlineGroup = combineGroup(onlineSeenDistanceGroup);
        List<Node> activeGroup = combineGroup(activeSeenDistanceGroup);

        List<Node> finalResult = prepareNodesResult(onlineGroup, activeGroup, 8, 2);
        return finalResult;
    }

    private static List<Node> unseen(DiscoverRequest request, String targetSex, GraphDatabaseService database, MetricRegistry metrics) {
        List<DistanceWrapper> online = onlineUnseenFilteredResult(request, targetSex, database, metrics);
        List<DistanceWrapper> active = activeUnseenFilteredResult(request, targetSex, database, metrics);

        Map<Long, List<Node>> onlineDistanceGroup = groupByDistance(online, 15_000L);
        sortDiscoverUnseenGroups(onlineDistanceGroup);
        Map<Long, List<Node>> activeDistanceGroup = groupByDistance(active, 15_000L);
        sortDiscoverUnseenGroups(activeDistanceGroup);

        List<Node> onlineGroup = combineGroup(onlineDistanceGroup);
        List<Node> activeGroup = combineGroup(activeDistanceGroup);

        List<Node> finalResult = prepareNodesResult(onlineGroup, activeGroup, 6, 4);
        return finalResult;
    }

    private static List<DistanceWrapper> onlineUnseenFilteredResult(DiscoverRequest request, String targetSex, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ONLINE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE, request.getFilter());
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static List<DistanceWrapper> activeUnseenFilteredResult(DiscoverRequest request, String targetSex, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ACTIVE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE, request.getFilter());
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static List<DistanceWrapper> onlineSeenFilteredResult(DiscoverRequest request, String targetSex, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ONLINE_USERS_GEO_SEEN_SORTED_BY_DISTANCE, request.getFilter());
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, limit, database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

    private static List<DistanceWrapper> activeSeenFilteredResult(DiscoverRequest request, String targetSex, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ACTIVE_USERS_GEO_SEEN_SORTED_BY_DISTANCE, request.getFilter());
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, limit, database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
        return result;
    }

}

class DistanceWrapper {
    Node node;
    long distance;
}
