package com.ringoid.api;

import com.ringoid.Relationships;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;

import java.util.ArrayList;
import java.util.List;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.api.Utils.sortLmmPhotos;
import static com.ringoid.api.Utils.sortLmmProfiles;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class Matches {

    public static LMMResponse matches(LMMRequest request, GraphDatabaseService database) {
        LMMResponse response = new LMMResponse();

        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
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
                    prof.setPhotoIds(sortLmmPhotos(sourceUser, eachProfile));
                    profileList.add(prof);
                }
                response.setProfiles(profileList);
            }
            tx.success();
        }//end transaction
        return response;
    }

}
