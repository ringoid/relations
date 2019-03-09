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
import static com.ringoid.api.Utils.sortMessagesProfiles;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class Messages {

    public static LMMResponse messages(LMMRequest request, GraphDatabaseService database) {
        LMMResponse response = new LMMResponse();

        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                List<Node> whoMessageWithMe = whoHasLikeMatchOrMessageWithMe(sourceUser, RelationshipType.withName(Relationships.MESSAGE.name()), Direction.BOTH);

                //IMPORTANT: we use 2 requests coz right now we don't use old and new part of message (feed)
                List<Node> messages = Utils.filterUsers(sourceUser, whoMessageWithMe, RelationshipType.withName(Relationships.VIEW_IN_MESSAGES.name()), true);
                messages.addAll(Utils.filterUsers(sourceUser, whoMessageWithMe, RelationshipType.withName(Relationships.VIEW_IN_MESSAGES.name()), false));

                messages = sortMessagesProfiles(sourceUser, messages);
                List<Profile> profileList = new ArrayList<>(messages.size());

                for (Node eachProfile : messages) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotoIds(sortLmmPhotos(sourceUser, eachProfile));
                    List<Message> msgs = Utils.messages(sourceUser, eachProfile);
                    prof.setMessages(msgs);
                    profileList.add(prof);
                }
                response.setProfiles(profileList);
            }
            tx.success();
        }//end transaction
        return response;
    }

}