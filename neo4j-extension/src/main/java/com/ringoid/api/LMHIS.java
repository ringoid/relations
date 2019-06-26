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
import static com.ringoid.api.Utils.markProfileWithLastMessageAt;
import static com.ringoid.api.Utils.sortLMHISPhotos;
import static com.ringoid.api.Utils.sortLMHISProfilesForInbox;
import static com.ringoid.api.Utils.sortLMHISProfilesForSent;
import static com.ringoid.api.Utils.sortLMHISUnseenPartProfiles;
import static com.ringoid.api.Utils.whoHasLikeMatchOrMessageWithMe;

public class LMHIS {
    private static final int MAX_PROFILES_IN_ONE_PART_OF_HELLOS = 200;
    private static final int MAX_PROFILES_IN_INBOX = 200;
    private static final int MAX_PROFILES_IN_SENT = 200;

    private static final Log log = LoggerFactory.getLogger(LMHIS.class);

    public static LMHISResponse lmHis(LMHISRequest request, GraphDatabaseService database) {
        LMHISResponse response = new LMHISResponse();
        try (Transaction tx = database.beginTx()) {

            Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), request.getUserId());
            if (Objects.isNull(sourceUser)) {
                log.warn("request lmHis for non exist user, userId [%s]", request.getUserId());
                response.setLastActionTime(request.getRequestedLastActionTime() - 1);
                tx.success();
                return response;
            }

            long actionTime = (Long) sourceUser.getProperty(LAST_ACTION_TIME.getPropertyName(), 0L);
            response.setLastActionTime(actionTime);
            if (request.getRequestedLastActionTime() <= actionTime) {
                List<Node> whoMessageWithMe = whoHasLikeMatchOrMessageWithMe(sourceUser, RelationshipType.withName(Relationships.MESSAGE.name()), Direction.BOTH);
                switch (request.getLmhisPart()) {
                    case "hellos": {
                        response.setProfiles(hellos(request, sourceUser, whoMessageWithMe, database));
                        break;
                    }
                    case "inbox": {
                        response.setProfiles(inbox(request, sourceUser, whoMessageWithMe, database));
                        break;
                    }
                    case "sent": {
                        response.setProfiles(sent(request, sourceUser, whoMessageWithMe, database));
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("unsupported lmHis part " + request.getLmhisPart());
                }
            }
            tx.success();
        }//end transaction
        return response;
    }

    private static List<Profile> hellos(LMHISRequest request, Node sourceUser,
                                        List<Node> whoMessageWithMe, GraphDatabaseService database) {

        List<Node> whoInMessages = Utils.filterUsers(sourceUser, whoMessageWithMe, RelationshipType.withName(Relationships.VIEW_IN_HELLOS.name()), request.isRequestNewPart());
        List<Profile> profileList = new ArrayList<>(whoInMessages.size());

        if (request.isRequestNewPart()) {
            whoInMessages = sortLMHISUnseenPartProfiles(sourceUser, whoInMessages);
        } else {
            whoInMessages = commonSortProfilesSeenPart(sourceUser, whoInMessages);
        }

        for (Node eachProfile : whoInMessages) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));

            List<Message> msgs = Utils.messages(sourceUser, eachProfile);
            if (msgs.isEmpty()) {
                continue;
            }

            boolean skipHimForHellos = false;
            for (Message ms : msgs) {
                if (ms.isWasYouSender()) {
                    skipHimForHellos = true;
                    break;
                }
            }
            if (skipHimForHellos) {
                continue;
            }
            prof.setMessages(msgs);

            prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
            //if user don't have photo right now - then skip him
            if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                continue;
            }
            prof = enrichProfile(eachProfile, sourceUser, prof);
            profileList.add(prof);
        }

        if (profileList.size() > MAX_PROFILES_IN_ONE_PART_OF_HELLOS) {
            profileList = profileList.subList(0, MAX_PROFILES_IN_ONE_PART_OF_HELLOS);
        }

        return profileList;
    }

    private static List<Profile> inbox(LMHISRequest request, Node sourceUser,
                                       List<Node> whoMessageWithMe, GraphDatabaseService database) {

        List<Profile> profileList = new ArrayList<>(whoMessageWithMe.size());

        for (Node eachProfile : whoMessageWithMe) {

            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));

            List<Message> msgs = Utils.messages(sourceUser, eachProfile);
            if (msgs.isEmpty()) {
                continue;
            }

            boolean haveMsgFromMe = false;
            boolean haveMsgFromHim = false;
            for (Message ms : msgs) {
                if (ms.isWasYouSender()) {
                    haveMsgFromMe = true;
                } else {
                    haveMsgFromHim = true;
                }
                if (haveMsgFromHim && haveMsgFromMe) {
                    break;
                }
            }
            if (!(haveMsgFromHim && haveMsgFromMe)) {
                continue;
            }
            prof.setMessages(msgs);

            prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
            //if user don't have photo right now - then skip him
            if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                continue;
            }
            prof = markProfileWithLastMessageAt(sourceUser, eachProfile, prof);
            prof = enrichProfile(eachProfile, sourceUser, prof);
            profileList.add(prof);
        }

        profileList = sortLMHISProfilesForInbox(profileList);

        if (profileList.size() > MAX_PROFILES_IN_INBOX) {
            profileList = profileList.subList(0, MAX_PROFILES_IN_INBOX);
        }

        return profileList;
    }

    private static List<Profile> sent(LMHISRequest request, Node sourceUser,
                                      List<Node> whoMessageWithMe, GraphDatabaseService database) {

        List<Profile> profileList = new ArrayList<>(whoMessageWithMe.size());

        for (Node eachProfile : whoMessageWithMe) {
            Profile prof = new Profile();
            prof.setUserId((String) eachProfile.getProperty(USER_ID.getPropertyName()));

            List<Message> msgs = Utils.messages(sourceUser, eachProfile);
            if (msgs.isEmpty()) {
                continue;
            }

            //if we don't have message from me then skip
            boolean skipHimForSent = true;
            for (Message ms : msgs) {
                if (ms.isWasYouSender()) {
                    skipHimForSent = false;
                    break;
                }
            }
            if (skipHimForSent) {
                continue;
            }
            prof.setMessages(msgs);

            prof.setPhotos(Utils.resizedAndVisibleToEveryOnePhotos(sortLMHISPhotos(sourceUser, eachProfile), request.getResolution(), database));
            //if user don't have photo right now - then skip him
            if (Objects.isNull(prof.getPhotos()) || prof.getPhotos().size() == 0) {
                continue;
            }

            prof = markProfileWithLastMessageAt(sourceUser, eachProfile, prof);

            prof = enrichProfile(eachProfile, sourceUser, prof);
            profileList.add(prof);
        }

        profileList = sortLMHISProfilesForSent(profileList);

        if (profileList.size() > MAX_PROFILES_IN_SENT) {
            profileList = profileList.subList(0, MAX_PROFILES_IN_SENT);
        }

        return profileList;
    }


}
