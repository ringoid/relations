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
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortProfilesByLastMessageAt;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class Messages {
    private static final Log log = LoggerFactory.getLogger(Messages.class);

    private static final int MAX_MESSAGES_PROFILES_NUM = 500;

    public static LMHISResponse messages(LMHISRequest request, GraphDatabaseService database) {
        LMHISResponse response = new LMHISResponse();

        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request messages for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                List<Node> whoMessageWithMe = whoHasLikeMatchOrMessageWithMe(sourceUser, RelationshipType.withName(Relationships.MESSAGE.name()), Direction.BOTH);

                //IMPORTANT: we use 2 requests coz right now we don't use old and new part of message (feed)
                List<Node> messages = Utils.filterUsers(sourceUser, whoMessageWithMe, RelationshipType.withName(Relationships.VIEW_IN_MESSAGES.name()), true);
                messages.addAll(Utils.filterUsers(sourceUser, whoMessageWithMe, RelationshipType.withName(Relationships.VIEW_IN_MESSAGES.name()), false));

                messages = sortProfilesByLastMessageAt(sourceUser, messages);
                List<Profile> profileList = new ArrayList<>(messages.size());

                for (Node eachProfile : messages) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
                    //if user don't have photo right now - then skip him
                    if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                        continue;
                    }
                    List<Message> msgs = Utils.messages(sourceUser, eachProfile);
                    prof = enrichProfile(eachProfile, sourceUser, prof);
                    prof.setMessages(msgs);
                    profileList.add(prof);
                }

                if (profileList.size() > MAX_MESSAGES_PROFILES_NUM) {
                    profileList = profileList.subList(0, MAX_MESSAGES_PROFILES_NUM);
                }

                response.setProfiles(profileList);

            }
            tx.success();
        }//end transaction
        return response;
    }

}
