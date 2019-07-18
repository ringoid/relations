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
import static com.ringoid.api.Utils.commonSortProfilesSeenPart;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortLMHISUnseenPartProfiles;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class LikesYou {
    private static final Log log = LoggerFactory.getLogger(LikesYou.class);

    private static final int MAX_LIKES_YOU_PROFILES_NUM = 500;

    public static LMHISResponse likesYou(LMHISRequest request, GraphDatabaseService database) {
        LMHISResponse response = new LMHISResponse();

        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request likes_you for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }
            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                List<Node> whoLikedMe = whoHasLikeMatchOrMessageWithMe(sourceUser, RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
                List<Node> liked = Utils.filterUsers(sourceUser, whoLikedMe, RelationshipType.withName(Relationships.VIEW_IN_LIKES_YOU.name()), request.isRequestNewPart());

                if (request.isRequestNewPart()) {
                    liked = sortLMHISUnseenPartProfiles(sourceUser, liked);
                } else {
                    liked = commonSortProfilesSeenPart(sourceUser, liked);
                }

                List<Profile> profileList = new ArrayList<>(liked.size());
                for (Node eachProfile : liked) {
                    Profile prof = new Profile();
                    prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));
                    prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
                    //if user don't have photo right now - then skip him
                    if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                        continue;
                    }
                    prof = enrichProfile(eachProfile, sourceUser, prof);
                    List<Message> messages = Utils.messages(sourceUser, eachProfile);
                    prof.setMessages(messages);
                    profileList.add(prof);

                }

                if (profileList.size() > MAX_LIKES_YOU_PROFILES_NUM) {
                    profileList = profileList.subList(0, MAX_LIKES_YOU_PROFILES_NUM);
                }

                response.setProfiles(profileList);
            }
            tx.success();
        }//end transaction
        return response;
    }

}
