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
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;

public class GetLCMessages {
    private static final Log log = LoggerFactory.getLogger(GetLCMessages.class);

    private static final int MAX_MESSAGES_IN_DIALOG = 150;

    public static LCResponse messages(LCRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        LCResponse response = new LCResponse();
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request get lc messages for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getLastActionTime() <= actionTime) {
                List<Node> all = allMatchesAndMessages(request, database, metrics);
                all = QueryUtils.sortProfilesByLastMessageAtOrMatchTime(sourceUser, all);
                List<Profile> profileList = new ArrayList<>(all.size());

                for (Node eachProfile : all) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
                    List<Message> msgs = Utils.messages(sourceUser, eachProfile, Chats.MAX_MESSAGES_NUM_IN_CHAT);
                    prof = enrichProfile(eachProfile, sourceUser, prof);
                    prof.setMessages(msgs);
                    prof.setUnseen(false);
                    profileList.add(prof);
                }

                if (profileList.size() > request.getLimit()) {
                    profileList = profileList.subList(0, request.getLimit());
                }

                response.setProfiles(profileList);
                int countAll = QueryUtils.count(QueryUtils.GET_LC_MESSAGES_NUM, request.getUserId(), database, metrics);
                response.setAllProfilesNum(countAll);
            }
            tx.success();
        }//end transaction
        return response;
    }

    private static List<Node> allMatchesAndMessages(LCRequest request, GraphDatabaseService database, MetricRegistry metrics) {
        String query = QueryUtils.constructFilteredQuery(QueryUtils.GET_LC_MESSAGES_AND_MATCHES_GEO, request.getFilter(), false);
        List<DistanceWrapper> result = QueryUtils.execute(query, request.getUserId(), "n/a", -1, -1, database, metrics);
        List<Node> finalResult = new ArrayList<>(result.size());
        for (DistanceWrapper each : result) {
            finalResult.add(each.node);
        }
        return finalResult;
    }
}
