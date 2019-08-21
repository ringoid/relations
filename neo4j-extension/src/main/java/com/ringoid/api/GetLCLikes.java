package com.ringoid.api;

import com.codahale.metrics.MetricRegistry;
import com.graphaware.common.log.LoggerFactory;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;

public class GetLCLikes {
    private static final Log log = LoggerFactory.getLogger(GetLCLikes.class);

    public static LCResponse likesYou(LCRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        LCResponse response = new LCResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request get lc likes for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getLastActionTime() <= actionTime) {

                List<Node> unseen = unseenFilteredResult(request, 0, request.getLimit(), database, metrics);
                log.info("unseen request return [%s] nodes", unseen.size());
                unseen = QueryUtils.sortGetLCUnseenPartProfiles(sourceUser, unseen);

                response.getProfiles().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), unseen, true, sourceUser, database, metrics));

                if (response.getProfiles().size() < request.getLimit()) {
                    List<Node> seen = seenFilteredResult(request, 0, request.getLimit(), database, metrics);
                    log.info("seen request return [%s] nodes", seen.size());
                    seen = QueryUtils.sortGetLCSeenPartProfiles(sourceUser, seen);

                    response.getProfiles().addAll(QueryUtils.createProfileListWithResizedAndSortedPhotos(request.getResolution(), seen, false, sourceUser, database, metrics));
                }

                if (response.getProfiles().size() > request.getLimit()) {
                    response.setProfiles(response.getProfiles().subList(0, request.getLimit()));
                }

                int countAll = QueryUtils.count(QueryUtils.GET_LC_LIKES_NUM, request.getUserId(), database, metrics);
                log.info("count all request return [%s] nodes", unseen.size());
                response.setAllProfilesNum(countAll);
            }

            tx.success();
        }//end transaction
        return response;
    }

    private static List<Node> unseenFilteredResult(LCRequest request, int skip, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.GET_LC_LIKES_GEO_UNSEEN_SORTED_BY_USER_ID, request.getFilter(), false);
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), "n/a", skip, limit, database, metrics);
        List<Node> finalResult = new ArrayList<>(result.size());
        for (DistanceWrapper each : result) {
            finalResult.add(each.node);
        }
        return finalResult;
    }

    private static List<Node> seenFilteredResult(LCRequest request, int skip, int limit, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.GET_LC_LIKES_GEO_SEEN_SORTED_BY_USER_ID, request.getFilter(), false);
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), "n/a", skip, limit, database, metrics);
        List<Node> finalResult = new ArrayList<>(result.size());
        for (DistanceWrapper each : result) {
            finalResult.add(each.node);
        }
        return finalResult;
    }

}
