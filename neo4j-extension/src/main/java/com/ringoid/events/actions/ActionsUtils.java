package com.ringoid.events.actions;

import com.ringoid.PhotoProperties;
import com.ringoid.Relationships;
import com.ringoid.ViewRelationshipSource;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.ringoid.BlockProperties.BLOCK_AT;
import static com.ringoid.BlockProperties.BLOCK_REASON_NUM;
import static com.ringoid.Labels.PERSON;
import static com.ringoid.Labels.PHOTO;
import static com.ringoid.LikeProperties.LIKED_AT;
import static com.ringoid.LikeProperties.LIKE_COUNT;
import static com.ringoid.MessageProperties.MSG_AT;
import static com.ringoid.MessageProperties.MSG_COUNT;
import static com.ringoid.PersonProperties.LIKE_COUNTER;
import static com.ringoid.PersonProperties.USER_ID;
import static com.ringoid.PhotoProperties.PHOTO_ID;
import static com.ringoid.ViewProperties.VIEW_AT;
import static com.ringoid.ViewProperties.VIEW_COUNT;
import static com.ringoid.ViewProperties.VIEW_TIME;
import static com.ringoid.common.UtilsInternaly.doWeHaveBlockInternaly;
import static com.ringoid.common.UtilsInternaly.getAllRelationshipBetweenNodes;
import static com.ringoid.common.UtilsInternaly.getAllRelationshipTypes;
import static com.ringoid.common.UtilsInternaly.getOrCreate;
import static com.ringoid.common.UtilsInternaly.getUploadedPhoto;
import static com.ringoid.common.UtilsInternaly.updateLastActionTime;

public class ActionsUtils {
    private static final int REPORT_REASON_TRESHOLD = 9;

    public static void message(UserMessageEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser) || Objects.isNull(targetUser)
                || sourceUser.getId() == targetUser.getId()) {
            return;
        }

        updateLastActionTime(sourceUser, event.getMessageAt(), database);
        if (doWeHaveBlockInternaly(sourceUser, targetUser, database)) {
            return;
        }

        Set<Relationships> existOutgoingRelationshipsBetweenProfiles = getAllRelationshipTypes(sourceUser, targetUser, Direction.OUTGOING);
        Set<Relationships> existIncomingRelationshipsBetweenProfiles = getAllRelationshipTypes(targetUser, sourceUser, Direction.OUTGOING);
        if (!existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MATCH) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE) &&
                !existIncomingRelationshipsBetweenProfiles.contains(Relationships.MATCH) &&
                !existIncomingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
            return;
        }

        //first create message rel with photo
        Optional<Node> targetPhotoOpt = getUploadedPhoto(targetUser, event.getOriginPhotoId());
        if (targetPhotoOpt.isPresent()) {
            Relationship msgRel = getOrCreate(sourceUser, targetPhotoOpt.get(), Direction.OUTGOING, Relationships.MESSAGE.name());
            int msgCount = (Integer) msgRel.getProperty(MSG_COUNT.getPropertyName(), 0);
            msgCount++;
            msgRel.setProperty(MSG_COUNT.getPropertyName(), msgCount);
        }

        List<Relationship> allRels = getAllRelationshipBetweenNodes(sourceUser, targetUser);
        Relationship existingMessage = null;
        for (Relationship each : allRels) {
            //could be only match or message, not both
            if (each.isType(RelationshipType.withName(Relationships.MATCH.name()))) {
                each.delete();
                existingMessage = getOrCreate(sourceUser, targetUser, Direction.OUTGOING, Relationships.MESSAGE.name());
                break;
            } else if (each.isType(RelationshipType.withName(Relationships.MESSAGE.name()))) {
                existingMessage = each;
                break;
            }
        }

        //todo:do we need this property?
        existingMessage.setProperty(MSG_AT.getPropertyName(), event.getMessageAt());

//      MessageEvent messageEvent = new MessageEvent(event.getUserId(), event.getTargetUserId(),
//                event.getText(), event.getUnixTime(), event.getMessageAt());
//
//        todo:implement
//      Utils.sendEventIntoInternalQueue(messageEvent, kinesis, streamName, event.getUserId(), gson);
//      send bot event
//      sendBotEvent(event.botEvent(), sqs, sqsUrl, botEnabled, gson);
    }

    public static void unlike(UserUnlikePhotoEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser) || Objects.isNull(targetUser)
                || sourceUser.getId() == targetUser.getId()) {
            return;
        }

        updateLastActionTime(sourceUser, event.getUnLikedAt(), database);
        if (doWeHaveBlockInternaly(sourceUser, targetUser, database)) {
            return;
        }

        Set<Relationships> existOutgoingRelationshipsBetweenProfiles = getAllRelationshipTypes(sourceUser, targetUser, Direction.OUTGOING);
        Set<Relationships> existIncomingRelationshipsBetweenProfiles = getAllRelationshipTypes(targetUser, sourceUser, Direction.OUTGOING);
        if (!existOutgoingRelationshipsBetweenProfiles.contains(Relationships.LIKE) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MATCH) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE) &&
                !existIncomingRelationshipsBetweenProfiles.contains(Relationships.MATCH) &&
                !existIncomingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
            return;
        }

        Optional<Node> targetPhotoOpt = getUploadedPhoto(targetUser, event.getOriginPhotoId());

        Iterable<Relationship> likesRels = sourceUser.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.OUTGOING);
        Relationship likeWithTargetProfileRel = null;
        int otherPhotoLikes = 0;
        for (Relationship eachLike : likesRels) {
            Node other = eachLike.getOtherNode(sourceUser);
            //if other node is a person -> check that it's target person
            if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == targetUser.getId()) {
                likeWithTargetProfileRel = eachLike;
            }//if other node is a photo (but not target) ->check was it uploaded by target user and count if yes
            else if (other.hasLabel(Label.label(PHOTO.getLabelName())) && (!targetPhotoOpt.isPresent() || other.getId() != targetPhotoOpt.get().getId())) {
                Iterable<Relationship> uploadedRels = other.getRelationships(Direction.INCOMING, RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()));
                for (Relationship eachUploaded : uploadedRels) {
                    if (eachUploaded.getOtherNode(other).getId() == targetUser.getId()) {
                        otherPhotoLikes++;
                    }
                }
            }//if we found target photo -> then unlike it
            else if (other.hasLabel(Label.label(PHOTO.getLabelName())) && targetPhotoOpt.isPresent() && other.getId() == targetPhotoOpt.get().getId()) {
                //increase like counter on the photo node
                Node targetPhoto = targetPhotoOpt.get();
                long photoLikeCounter = (Long) targetPhoto.getProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), 0L);
                if (photoLikeCounter > 0) {
                    photoLikeCounter--;
                }
                targetPhoto.setProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), photoLikeCounter);
                eachLike.delete();
            }
        }

        if (otherPhotoLikes != 0) {
            return;
        }

        //there is no other likes, so delete relationships between profiles
        if (Objects.nonNull(likeWithTargetProfileRel)) {
            likeWithTargetProfileRel.delete();
            return;
        }

        //so there is no other likes for photo from source user -> break relationships between profiles
        Iterable<Relationship> profileRels = sourceUser.getRelationships(
                RelationshipType.withName(Relationships.MATCH.name()),
                RelationshipType.withName(Relationships.MESSAGE.name())
        );
        for (Relationship eachRel : profileRels) {
            Node other = eachRel.getOtherNode(sourceUser);
            if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == targetUser.getId()) {
                eachRel.delete();
            }
        }

        //now we need to decide which relationship create between profiles instead deleted
        Set<Long> photoIds = new HashSet<>();
        Iterable<Relationship> uploadedRels = sourceUser.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
        for (Relationship uploaded : uploadedRels) {
            Node other = uploaded.getOtherNode(sourceUser);
            if (other.hasLabel(Label.label(PHOTO.getLabelName()))) {
                photoIds.add(other.getId());
            }
        }

        Iterable<Relationship> targetUserLikesRels = targetUser.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.OUTGOING);
        for (Relationship like : targetUserLikesRels) {
            Node other = like.getOtherNode(targetUser);
            if (other.hasLabel(Label.label(PHOTO.getLabelName())) && photoIds.contains(other.getId())) {
                Relationship newLike = getOrCreate(targetUser, sourceUser, Direction.OUTGOING, Relationships.LIKE.name());
                //todo:do we need this property
                newLike.setProperty(LIKED_AT.getPropertyName(), System.currentTimeMillis());
                return;
            }
        }
    }

    public static void block(UserBlockOtherEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser) || Objects.isNull(targetUser)
                || sourceUser.getId() == targetUser.getId()) {
            return;
        }

        updateLastActionTime(sourceUser, event.getBlockedAt(), database);
        if (doWeHaveBlockInternaly(sourceUser, targetUser, database)) {
            return;
        }

        Set<Relationships> existOutgoingRelationshipsBetweenProfiles = getAllRelationshipTypes(sourceUser, targetUser, Direction.OUTGOING);
        if (!existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
            return;
        }

        //delete all photo relationships
        //todo:mb we don't need to delete all relationship (we can keep VIEW for ex here)
        Iterable<Relationship> uploadRels = sourceUser.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
        for (Relationship uploaded : uploadRels) {
            Node photoNode = uploaded.getOtherNode(sourceUser);
            if (photoNode.hasLabel(Label.label(PHOTO.getLabelName()))) {
                Iterable<Relationship> incomingRels = photoNode.getRelationships(Direction.INCOMING);
                for (Relationship incoming : incomingRels) {
                    if (incoming.getOtherNode(photoNode).getId() == targetUser.getId()) {
                        incoming.delete();
                    }
                }
            }
        }

        uploadRels = targetUser.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
        Node targetPhoto = null;
        for (Relationship uploaded : uploadRels) {
            Node photoNode = uploaded.getOtherNode(targetUser);
            if (photoNode.hasLabel(Label.label(PHOTO.getLabelName()))) {
                String photoId = (String) photoNode.getProperty(PHOTO_ID.getPropertyName());
                if (Objects.equals(photoId, event.getOriginPhotoId())) {
                    targetPhoto = photoNode;
                }
                Iterable<Relationship> incomingRels = photoNode.getRelationships(Direction.INCOMING);
                for (Relationship incoming : incomingRels) {
                    if (incoming.getOtherNode(photoNode).getId() == sourceUser.getId()) {
                        incoming.delete();
                    }
                }
            }
        }

        //delete all profile relationships
        Iterable<Relationship> anyRels = getAllRelationshipBetweenNodes(sourceUser, targetUser);
        for (Relationship any : anyRels) {
            any.delete();
        }

        //create a block
        Relationship block = getOrCreate(sourceUser, targetUser, Direction.OUTGOING, Relationships.BLOCK.name());
        block.setProperty(BLOCK_REASON_NUM.getPropertyName(), event.getBlockReasonNum());
        //todo:do we need this property?
        block.setProperty(BLOCK_AT.getPropertyName(), event.getBlockedAt());

        if (Objects.nonNull(targetPhoto)) {
            block = getOrCreate(sourceUser, targetPhoto, Direction.OUTGOING, Relationships.BLOCK.name());
            block.setProperty(BLOCK_REASON_NUM.getPropertyName(), event.getBlockReasonNum());
            //todo:do we need this property
            block.setProperty(BLOCK_AT.getPropertyName(), event.getBlockedAt());
        }

//        currently users couldn't block itself concurrently (only first block will be applied
//        if (event.getBlockReasonNum() > REPORT_REASON_TRESHOLD && relCreated > 0) {
//        todo:implement
//            Utils.markPersonForModeration(event.getTargetUserId(), tx);
//            Utils.sendEventIntoInternalQueue(event, kinesis, streamName, event.getTargetUserId(), gson);
//            return;
//        }
//
//        if it's just block - we can delete the conversation
//        DeleteUserConversationEvent deleteUserConversationEvent = new DeleteUserConversationEvent(event.getUserId(), event.getTargetUserId());
//        Utils.sendEventIntoInternalQueue(deleteUserConversationEvent, kinesis, streamName, deleteUserConversationEvent.getUserId(), gson);

    }

    public static void viewPhoto(UserViewPhotoEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser) || Objects.isNull(targetUser)
                || sourceUser.getId() == targetUser.getId()) {
            return;
        }

        updateLastActionTime(sourceUser, event.getViewAt(), database);
        if (doWeHaveBlockInternaly(sourceUser, targetUser, database)) {
            return;
        }
        ViewRelationshipSource source = ViewRelationshipSource.fromString(event.getSource());
        Relationships targetPhotoRelationship = Relationships.VIEW;
        Relationships targetProfileRelationship;
        switch (source) {
            case NEW_FACES:
                targetProfileRelationship = Relationships.VIEW;
                break;
            case WHO_LIKED_ME:
                targetProfileRelationship = Relationships.VIEW_IN_LIKES_YOU;
                break;
            case MATCHES:
                targetProfileRelationship = Relationships.VIEW_IN_MATCHES;
                break;
            case MESSAGES:
                targetProfileRelationship = Relationships.VIEW_IN_MESSAGES;
                break;
            case CHAT:
                //todo:discuss this
                targetProfileRelationship = Relationships.VIEW;
                break;
            default:
                throw new IllegalArgumentException("Unsupported source " + source.getValue());
        }

        Optional<Node> targetPhotoOpt = getUploadedPhoto(targetUser, event.getOriginPhotoId());
        if (targetPhotoOpt.isPresent()) {
            //photo is still here, so create view event to photo
            Node targetPhoto = targetPhotoOpt.get();
            Relationship photoView = getOrCreate(sourceUser, targetPhoto, Direction.OUTGOING, targetPhotoRelationship.name());

            long viewCount = (Long) photoView.getProperty(VIEW_COUNT.getPropertyName(), 0L);
            long viewTimeSec = (Long) photoView.getProperty(VIEW_TIME.getPropertyName(), 0L);

            viewCount += event.getViewCount();
            photoView.setProperty(VIEW_COUNT.getPropertyName(), viewCount);

            viewTimeSec += event.getViewTimeMillis();
            photoView.setProperty(VIEW_TIME.getPropertyName(), viewTimeSec);

            //todo:check do we really need this property on relationship with photo level
            //what we should use last or first time?
            photoView.setProperty(VIEW_AT.getPropertyName(), event.getViewAt());
        }

        Relationship profileView = getOrCreate(sourceUser, targetUser, Direction.OUTGOING, targetProfileRelationship.name());
        profileView.setProperty(VIEW_AT.getPropertyName(), event.getViewAt());

        Iterable<Relationship> allOutgoingRels = getAllRelationshipBetweenNodes(sourceUser, targetUser);
        Set<String> toDelete = new HashSet<>();
        toDelete.add(Relationships.VIEW.name());
        toDelete.add(Relationships.VIEW_IN_LIKES_YOU.name());
        toDelete.add(Relationships.VIEW_IN_MATCHES.name());
        toDelete.add(Relationships.VIEW_IN_MESSAGES.name());

        if (targetProfileRelationship == Relationships.VIEW_IN_MESSAGES) {
            toDelete.remove(Relationships.VIEW_IN_MESSAGES.name());
        } else if (targetProfileRelationship == Relationships.VIEW_IN_MATCHES) {
            toDelete.remove(Relationships.VIEW_IN_MATCHES.name());
        } else if (targetProfileRelationship == Relationships.VIEW_IN_LIKES_YOU) {
            toDelete.remove(Relationships.VIEW_IN_LIKES_YOU.name());
        } else if (targetProfileRelationship == Relationships.VIEW) {
            toDelete.remove(Relationships.VIEW.name());
        }

        for (Relationship each : allOutgoingRels) {
            if (each.getStartNode().getId() == sourceUser.getId() && toDelete.contains(each.getType().name())) {
                each.delete();
            }
        }
    }

    public static void likePhoto(UserLikePhotoEvent event, GraphDatabaseService database) {
        Node sourceUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getUserId());
        Node targetUser = database.findNode(Label.label(PERSON.getLabelName()), USER_ID.getPropertyName(), event.getTargetUserId());
        if (Objects.isNull(sourceUser) || Objects.isNull(targetUser)
                || sourceUser.getId() == targetUser.getId()) {
            return;
        }

        updateLastActionTime(sourceUser, event.getLikedAt(), database);
        if (doWeHaveBlockInternaly(sourceUser, targetUser, database)) {
            return;
        }

        Set<Relationships> existOutgoingRelationshipsBetweenProfiles = getAllRelationshipTypes(sourceUser, targetUser, Direction.OUTGOING);
        if (!existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_LIKES_YOU) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MESSAGES) &&
                !existOutgoingRelationshipsBetweenProfiles.contains(Relationships.VIEW_IN_MATCHES)) {
            return;
        }
        existOutgoingRelationshipsBetweenProfiles.remove(Relationships.VIEW);
        existOutgoingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_LIKES_YOU);
        existOutgoingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MATCHES);
        existOutgoingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MESSAGES);

        //todo:make defense more stronger and check that source has VIEW with photo also

        //first like photo if it exist
        Optional<Node> targetPhotoOpt = getUploadedPhoto(targetUser, event.getOriginPhotoId());
        if (targetPhotoOpt.isPresent()) {
            Node targetPhoto = targetPhotoOpt.get();
            Relationship photoLike = getOrCreate(sourceUser, targetPhoto, Direction.OUTGOING, Relationships.LIKE.name());

            long likeCount = (Long) photoLike.getProperty(LIKE_COUNT.getPropertyName(), 0L);
            likeCount += event.getLikeCount();
            photoLike.setProperty(LIKE_COUNT.getPropertyName(), likeCount);

            //todo:check do we really need this property on relationship with photo level
            //what we should use last or first time?
            photoLike.setProperty(LIKED_AT.getPropertyName(), event.getLikedAt());

            //increase like counter on the person node
            long likeCounter = (Long) targetUser.getProperty(LIKE_COUNTER.getPropertyName(), 0L);
            likeCounter++;
            targetUser.setProperty(LIKE_COUNTER.getPropertyName(), likeCounter);

            //increase like counter on the photo node
            long photoLikeCounter = (Long) targetPhoto.getProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), 0L);
            photoLikeCounter++;
            targetPhoto.setProperty(PhotoProperties.LIKE_COUNTER.getPropertyName(), photoLikeCounter);

//        todo:send invocation
//        PhotoLikeEvent likeEvent = new PhotoLikeEvent(event.getTargetUserId(), event.getOriginPhotoId());
//        Utils.sendEventIntoInternalQueue(likeEvent, kinesis, streamName, event.getTargetUserId(), gson);
//        send bot event
//        sendBotEvent(event.botUserLikePhotoEvent(), sqs, sqsUrl, botEnabled, gson);
        }

        Set<Relationships> existIncomingRelationshipsBetweenProfiles = getAllRelationshipTypes(sourceUser, targetUser, Direction.INCOMING);
        existIncomingRelationshipsBetweenProfiles.remove(Relationships.VIEW);
        existIncomingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_LIKES_YOU);
        existIncomingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MATCHES);
        existIncomingRelationshipsBetweenProfiles.remove(Relationships.VIEW_IN_MESSAGES);

        //now check which type of relationships we should create between profiles
        //if there is a like (outgoing) already, match or a message then we should return
        if (existOutgoingRelationshipsBetweenProfiles.contains(Relationships.LIKE) ||
                existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MATCH) ||
                existOutgoingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE) ||
                existIncomingRelationshipsBetweenProfiles.contains(Relationships.MATCH) ||
                existIncomingRelationshipsBetweenProfiles.contains(Relationships.MESSAGE)) {
            return;
        }

        //if there is no any relationships except view of any kind, then create a like
        if (existOutgoingRelationshipsBetweenProfiles.isEmpty() && existIncomingRelationshipsBetweenProfiles.isEmpty()) {
            Relationship likeProfile = getOrCreate(sourceUser, targetUser, Direction.OUTGOING, Relationships.LIKE.name());
            //todo:do we need this property?
            long likeAt = (Long) likeProfile.getProperty(LIKED_AT.getPropertyName(), 0L);
            if (likeAt == 0L) {
                likeProfile.setProperty(LIKED_AT.getPropertyName(), event.getLikedAt());
            }
            return;
        }

        //now the difficult case
        if (existIncomingRelationshipsBetweenProfiles.contains(Relationships.LIKE)) {
            Iterable<Relationship> rels = sourceUser.getRelationships(RelationshipType.withName(Relationships.LIKE.name()), Direction.INCOMING);
            Relationship incommingLike = null;
            for (Relationship rel : rels) {
                Node other = rel.getOtherNode(sourceUser);
                if (other.hasLabel(Label.label(PERSON.getLabelName())) && other.getId() == targetUser.getId()) {
                    incommingLike = rel;
                    break;
                }
            }

            //delete incomming like
            incommingLike.delete();

            //now we should decide - do we need to create a match
            // or message (could be possible if users have message already and then one of them unlike)
            List<Node> sourcePhotoNodes = new ArrayList<>();
            List<Node> targetPhotoNodes = new ArrayList<>();

            Iterable<Relationship> sourceUploadRel = sourceUser.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship each : sourceUploadRel) {
                Node other = each.getOtherNode(sourceUser);
                if (other.hasLabel(Label.label(PHOTO.getLabelName()))) {
                    sourcePhotoNodes.add(other);
                }
            }

            Iterable<Relationship> targetUploadRel = targetUser.getRelationships(RelationshipType.withName(Relationships.UPLOAD_PHOTO.name()), Direction.OUTGOING);
            for (Relationship each : targetUploadRel) {
                Node other = each.getOtherNode(targetUser);
                if (other.hasLabel(Label.label(PHOTO.getLabelName()))) {
                    targetPhotoNodes.add(other);
                }
            }

            //now choose and create relationship
            for (Node eachPhoto : sourcePhotoNodes) {
                List<Relationship> anyRels = getAllRelationshipBetweenNodes(eachPhoto, targetUser);
                for (Relationship each : anyRels) {
                    if (each.isType(RelationshipType.withName(Relationships.MESSAGE.name()))) {
                        //create a message
                        Relationship messRel = getOrCreate(targetUser, sourceUser, Direction.OUTGOING, Relationships.MESSAGE.name());
                        //todo:do we need this property?
                        messRel.setProperty(MSG_AT.getPropertyName(), System.currentTimeMillis());
                        return;
                    }
                }
            }

            for (Node eachPhoto : targetPhotoNodes) {
                List<Relationship> anyRels = getAllRelationshipBetweenNodes(eachPhoto, sourceUser);
                for (Relationship each : anyRels) {
                    if (each.isType(RelationshipType.withName(Relationships.MESSAGE.name()))) {
                        //create a message
                        Relationship messRel = getOrCreate(sourceUser, targetUser, Direction.OUTGOING, Relationships.MESSAGE.name());
                        //todo:do we need this property?
                        messRel.setProperty(MSG_AT.getPropertyName(), System.currentTimeMillis());
                        return;
                    }
                }
            }

            getOrCreate(sourceUser, targetUser, Direction.OUTGOING, Relationships.MATCH.name());
        }

    }

//    private static void sendBotEvent(Object event, AmazonSQS sqs, String sqsUrl, boolean botEnabled, Gson gson) {
//        log.debug("try to send bot event {}, botEnabled {}", event, botEnabled);
//        if (!botEnabled) {
//            return;
//        }
//        log.debug("send bot event {} into sqs queue", event);
//        sqs.sendMessage(sqsUrl, gson.toJson(event));
//        log.debug("successfully send bot event {} into sqs queue", event);
//    }

}
