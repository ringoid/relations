package com.ringoid.api;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.api.Utils.sortLmmPhotos;
import static com.ringoid.api.Utils.sortLmmProfiles;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class Matches {
    private static final Log log = LoggerFactory.getLogger(Matches.class);

    private static final int MAX_MATCH_PROFILES_NUM = 100;

    public static LMHISResponse matches(LMHISRequest request, GraphDatabaseService database) {
        LMHISResponse response = new LMHISResponse();

        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request matches for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                List<Node> whoMatchedWithMe = whoHasLikeMatchOrMessageWithMe(sourceUser, RelationshipType.withName(Relationships.MATCH.name()), Direction.BOTH);
                List<Node> matched = Utils.filterUsers(sourceUser, whoMatchedWithMe, RelationshipType.withName(Relationships.VIEW_IN_MATCHES.name()), request.isRequestNewPart());

                matched = sortLmmProfiles(sourceUser, matched, true);
                List<Profile> profileList = new ArrayList<>(matched.size());
                for (Node eachProfile : matched) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLmmPhotos(sourceUser, eachProfile), request.getResolution(), database));
                    //if user don't have photo right now - then skip him
                    if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                        continue;
                    }
                    List<Message> messages = Utils.messages(sourceUser, eachProfile);
                    prof.setMessages(messages);
                    profileList.add(prof);
                }

                if (profileList.size() > MAX_MATCH_PROFILES_NUM) {
                    profileList = profileList.subList(0, MAX_MATCH_PROFILES_NUM);
                }

                response.setProfiles(profileList);
            }
            tx.success();
        }//end transaction
        return response;
    }

}
