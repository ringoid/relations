package com.ringoid.api;

import com.graphaware.common.log.LoggerFactory;
import com.ringoid.Labels;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.List;
import java.util.Objects;

import static com.ringoid.Labels.PERSON;
import static com.ringoid.PersonProperties.LAST_ACTION_TIME;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.api.Utils.enrichProfile;
import static com.ringoid.api.Utils.sortLMHISPhotos;

public class Chats {
    private static final Log log = LoggerFactory.getLogger(Chats.class);

    public static ChatResponse chat(ChatRequest request, GraphDatabaseService database) {
        ChatResponse response = new ChatResponse();
        response.setChatExists(false);
        try (Transaction tx = database.beginTx()) {
            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request chat for non exist user, userId [%s]", request.getUserId());
                //return requested last action time, coz here user must be exist already
                response.setLastActionTime(request.getRequestedLastActionTime());
                tx.success();
                return response;
            }
            Node oppositeUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getOppositeUserId());
            if (Objects.isNull(oppositeUser)) {
                log.warn("request chat for non exist opposite user, oppositeUser [%s]", request.getOppositeUserId());
                //return requested last action time, coz here user must be exist already
                response.setLastActionTime(request.getRequestedLastActionTime());
                tx.success();
                return response;
            }

            if (oppositeUser.hasLabel(Label.label(Labels.HIDDEN.getLabelName()))) {
                log.warn("request chat for hidden opposite user, oppositeUser [%s]", request.getOppositeUserId());
                //return requested last action time, coz here user must be exist already
                response.setLastActionTime(request.getRequestedLastActionTime());
                tx.success();
                return response;
            }

            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                Profile prof = new Profile();
                prof.setUserId(request.getOppositeUserId());
                prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, oppositeUser), request.getResolution(), database));
                //if user don't have photo right now - then skip him
                if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().isEmpty()) {
                    tx.success();
                    return response;
                }
                prof = enrichProfile(oppositeUser, sourceUser, prof);
                response.setProfile(prof);

                List<Message> msgs = Utils.messages(sourceUser, oppositeUser);
                if (Objects.isNull(msgs) || msgs.isEmpty()) {
                    tx.success();
                    return response;
                }
                
                prof.setMessages(msgs);
                response.setChatExists(true);
            }

            tx.success();
        }//end transaction
        return response;
    }
}
