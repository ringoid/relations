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
import java.util.Comparator;
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
//        log.info("handle discover request %s", request);
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

                boolean useKievLocation = !QueryUtils.locationExists(request.getUserId(), database, metrics);

                long startUnseen = System.currentTimeMillis();
                List<Node> unseen = unseen(request, targetSex, useKievLocation, database, metrics);
                long loopTime = System.currentTimeMillis() - startUnseen;
                metrics.histogram("discover_unseen_part").update(loopTime);

                response.getNewFaces().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), unseen, true, sourceUser, database, metrics));

                int resultSize = request.getLimit();
                if (response.getNewFaces().isEmpty()) {
                    resultSize = HARDCODED_MAX_FEED_NUM;
                }


                if (response.getNewFaces().size() < resultSize) {
                    long startSeen = System.currentTimeMillis();
                    List<Node> seen = seen(sourceUser, request, database, metrics);
                    loopTime = System.currentTimeMillis() - startSeen;
                    metrics.histogram("discover_seen_part").update(loopTime);

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

    private static Map<Long, List<DistanceWrapper>> groupByDistance(List<DistanceWrapper> source, long step) {
        if (source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<DistanceWrapper>> map = new HashMap<>();
        for (DistanceWrapper each : source) {
            long eachDistance = each.distance;
            long tmp = eachDistance / step;
            if (tmp == 0L) {
                tmp = 1L;
            }
            if (eachDistance % step > 0) {
                tmp++;
            }
            List<DistanceWrapper> part = map.get(tmp);
            if (Objects.isNull(part)) {
                part = new ArrayList<>();
            }
            part.add(each);
            map.put(tmp, part);
        }
        return map;
    }

    private static void sortDiscoverUnseenGroups(Map<Long, List<DistanceWrapper>> groups) {
        for (Map.Entry<Long, List<DistanceWrapper>> each : groups.entrySet()) {
            QueryUtils.sortDiscoverUnseenPartProfiles(each.getValue());
        }
    }

    private static void sortDiscoverSeenGroups(Node source, Map<Long, List<DistanceWrapper>> groups) {
        for (Map.Entry<Long, List<DistanceWrapper>> each : groups.entrySet()) {
            QueryUtils.discoverSortProfilesSeenPart(source, each.getValue());
        }
    }

    private static List<DistanceWrapper> combineGroup(Map<Long, List<DistanceWrapper>> source) {
        List<DistanceWrapper> result = new ArrayList<>();
        List<Long> keys = new ArrayList<>(source.keySet());
        Collections.sort(keys);
        for (Long eachKey : keys) {
            result.addAll(source.get(eachKey));
        }
        return result;
    }

    private static List<Node> prepareNodesResult(List<DistanceWrapper> onlineGroup, List<DistanceWrapper> activeGroup,
                                                 int onlineNum, int activeNum) {
        List<Node> result = new ArrayList<>();
        while (true) {
            List<DistanceWrapper> tmpResult = new ArrayList<>(10);
            Iterator<DistanceWrapper> onlineIt = onlineGroup.iterator();
            Iterator<DistanceWrapper> activeIt = activeGroup.iterator();
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

            //currently we first sort by distance (later should delete)
            Collections.sort(tmpResult, new Comparator<DistanceWrapper>() {
                @Override
                public int compare(DistanceWrapper o1, DistanceWrapper o2) {
                    return Long.compare(o1.distance, o2.distance);
                }
            });

            for (DistanceWrapper each : tmpResult) {
                result.add(each.node);
            }

            //after that (when we delete previous hack) we need to shuffle
            //Collections.shuffle(tmpResult);
            //result.addAll(tmpResult);
        }
    }

    private static List<Node> seen(Node sourceNode, DiscoverRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        Map<String, List<DistanceWrapper>> seen = QueryUtils.seen(sourceNode, request.getFilter(), 100, database, metrics);

        List<DistanceWrapper> onlineSeen = seen.get("online");
        onlineSeen = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), onlineSeen);
        List<DistanceWrapper> activeSeen = seen.get("active");
        activeSeen = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), activeSeen);

        Map<Long, List<DistanceWrapper>> onlineSeenDistanceGroup = groupByDistance(onlineSeen, 15_000L);
        sortDiscoverSeenGroups(sourceNode, onlineSeenDistanceGroup);
        Map<Long, List<DistanceWrapper>> activeSeenDistanceGroup = groupByDistance(activeSeen, 15_000L);
        sortDiscoverSeenGroups(sourceNode, activeSeenDistanceGroup);

        List<DistanceWrapper> onlineGroup = combineGroup(onlineSeenDistanceGroup);
        List<DistanceWrapper> activeGroup = combineGroup(activeSeenDistanceGroup);

        List<Node> finalResult = prepareNodesResult(onlineGroup, activeGroup, 8, 2);
        return finalResult;
    }

    private static List<DistanceWrapper> filterByDistance(List<DistanceWrapper> source, Integer maxDistance) {
        if (Objects.isNull(maxDistance)) {
            return source;
        }
        long maxD = new Long(maxDistance);
        Iterator<DistanceWrapper> it = source.iterator();
        while (it.hasNext()) {
            DistanceWrapper ds = it.next();
            if (ds.distance > maxD) {
                it.remove();
            }
        }
        return source;
    }

    private static List<Node> unseen(DiscoverRequest request, String targetSex, boolean useKievLocation, GraphDatabaseService database, MetricRegistry metrics) {
        //make some hack, step 1
        Integer maxDistance = null;
        if (Objects.nonNull(request.getFilter()) && Objects.nonNull(request.getFilter().getMaxDistance())) {
            maxDistance = request.getFilter().getMaxDistance();
            request.getFilter().setMaxDistance(null);
        }
        //end step 1

        List<DistanceWrapper> online = onlineUnseenFilteredResult(request, targetSex, useKievLocation, database, metrics);
        List<DistanceWrapper> active = activeUnseenFilteredResult(request, targetSex, useKievLocation, database, metrics);

        //make some hack, step 2
        online = filterByDistance(online, maxDistance);
        active = filterByDistance(active, maxDistance);
        if (Objects.nonNull(request.getFilter()) && Objects.nonNull(maxDistance)) {
            request.getFilter().setMaxDistance(maxDistance);
        }
        //end step 2

        Map<Long, List<DistanceWrapper>> onlineDistanceGroup = groupByDistance(online, 15_000L);
        sortDiscoverUnseenGroups(onlineDistanceGroup);
        Map<Long, List<DistanceWrapper>> activeDistanceGroup = groupByDistance(active, 15_000L);
        sortDiscoverUnseenGroups(activeDistanceGroup);

        List<DistanceWrapper> onlineGroup = combineGroup(onlineDistanceGroup);
        List<DistanceWrapper> activeGroup = combineGroup(activeDistanceGroup);

        List<Node> finalResult = prepareNodesResult(onlineGroup, activeGroup, 6, 4);
        return finalResult;
    }

    private static List<DistanceWrapper> onlineUnseenFilteredResult(DiscoverRequest request, String targetSex, boolean useKievLocation, GraphDatabaseService database, MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ONLINE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE, request.getFilter(), useKievLocation);
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
//        log.info("onlineUnseenFilteredResult for userId [%s] size : [%s]", request.getUserId(), Integer.toString(result.size()));
        metrics.histogram("discover_online_unseen_filtered_result").update(System.currentTimeMillis() - start);
        return result;
    }

    private static List<DistanceWrapper> activeUnseenFilteredResult(DiscoverRequest request, String targetSex, boolean useKievLocation, GraphDatabaseService database, MetricRegistry metrics) {
        long start = System.currentTimeMillis();
        String query = QueryUtils.constructFilteredQuery(QueryUtils.DISCOVER_ACTIVE_USERS_GEO_NOT_SEEN_SORTED_BY_DISTANCE, request.getFilter(), useKievLocation);
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), targetSex, 0, request.getLimit(), database, metrics);
        result = QueryUtils.filterNodesByVisiblePhotos(request.getUserId(), result);
//        log.info("activeUnseenFilteredResult for userId [%s] size : [%s]", request.getUserId(), Integer.toString(result.size()));
        metrics.histogram("discover_active_unseen_filtered_result").update(System.currentTimeMillis() - start);
        return result;
    }

}

class DistanceWrapper {
    Node node;
    long distance;
}
